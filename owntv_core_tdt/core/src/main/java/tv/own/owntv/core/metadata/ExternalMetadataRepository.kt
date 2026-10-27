package tv.own.owntv.core.metadata

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.core.settings.SettingsRepository
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Lazy, bounded enrichment shared by the channel drawer, player HUD and VOD details. */
class ExternalMetadataRepository(
    context: Context,
    private val provider: MetadataProvider,
    private val client: ExternalMetadataClient,
    private val settings: SettingsRepository,
    val store: ExternalMetadataStore,
    private val profiles: ProfileDao,
) {
    private val directory = File(context.cacheDir, "external-title-metadata")
    private val lock = Mutex()

    suspend fun configure(config: ExternalMetadataConfig) = withContext(Dispatchers.IO) {
        lock.withLock { store.save(config); client.clearSession() }
    }

    suspend fun live(rawTitle: String, details: EpgDetails?): ExternalTitleMetadata? = resolve(
        ExternalMetadataRules.liveTitle(rawTitle), details?.year, null, null, details,
    )

    suspend fun known(tmdbId: Int, type: MetadataType): ExternalTitleMetadata? {
        if (type == MetadataType.EPISODE || tmdbId <= 0) return null
        return resolve("", null, tmdbId, type, null)
    }

    private suspend fun resolve(title: String, year: Int?, knownId: Int?, type: MetadataType?, epg: EpgDetails?): ExternalTitleMetadata? = withContext(Dispatchers.IO) {
        val cfg = store.config.value
        val metadata = settings.metadataConfig()
        if (!cfg.enabled || !metadata.enabled || (knownId == null && title.length < 3)) return@withContext null
        val includeAdult = profileAllowsAdultMetadata(profiles.getById(settings.activeProfileIdNow())?.isKids)
        // Separate type, metadata language, maturity and credential configurations. No raw keys in filenames.
        val key = hash(listOf(ARTWORK_CACHE_VERSION, title, year, knownId, type, epg?.categories, epg?.episode, metadata.resolvedLanguage, includeAdult, cfg.json()).joinToString("|"))
        lock.withLock {
            read(key)?.let { return@withLock it }
            val file = File(directory, key)
            // A cached explicit miss is different from a failed network request.
            if (file.exists() && readMiss(file)) return@withLock null
            val match = if (knownId == null) {
                val hits = mutableListOf<MetadataSearchResult>()
                val categories = epg?.categoryList.orEmpty().joinToString(" ").lowercase(java.util.Locale.ROOT)
                val movieOnly = listOf("película", "pelicula", "movie", "film", "cine").any { categories.contains(it) }
                val tvOnly = !epg?.episode.isNullOrBlank() || categories.contains("series") || categories.contains("serie")
                if (!tvOnly) hits += provider.searchMovie(title, year, includeAdult) ?: return@withLock null
                if (!movieOnly || tvOnly) hits += provider.searchTv(title, if (tvOnly) null else year, includeAdult) ?: return@withLock null
                val selected = ExternalMetadataRules.choose(title, if (tvOnly) null else year, hits)
                if (selected == null) { write(key, null, 6 * HOUR); return@withLock null }
                selected.tmdbId to selected.type
            } else knownId to requireNotNull(type)
            val full = if (match.second == MetadataType.TV) provider.tvDetails(match.first) else provider.movieDetails(match.first)
            full ?: return@withLock null
            if (full.tmdbId != match.first) return@withLock null
            var result = ExternalTitleMetadata(full.tmdbId, match.second, full.title,
                tvdbId = full.tvdbId,
                tmdbRating = full.rating?.takeIf { it.isFinite() && it > 0 && it <= 10 },
                posterUrl = MetadataImages.poster(full.posterPath), logoUrl = MetadataImages.logo(full.logoPath),
                overview = full.overview)
            var failed = false
            if (cfg.tvdbKey.isNotBlank() && result.type == MetadataType.TV && result.tvdbId != null) {
                try {
                    val data = client.tvdb(requireNotNull(result.tvdbId), cfg, metadata.resolvedLanguage)
                    result = result.copy(tvdbOverview = data.overview, tvdbArtwork = data.artwork, tvdbUrl = data.url, tvdbLoaded = true, logoUrl = data.logo ?: result.logoUrl)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { failed = true }
            }
            coroutineContext.ensureActive()
            write(key, result, if (failed) 5 * 60_000L else 7 * 24 * HOUR)
            result
        }
    }

    /** Test the configured services without searching a title or exposing exception text. */
    suspend fun test(): Boolean? = withContext(Dispatchers.IO) {
        lock.withLock {
            val cfg = store.config.value
            suspend fun attempt(block: suspend () -> Unit): Boolean = try { block(); true }
                catch (error: CancellationException) { throw error } catch (_: Exception) { false }
            val tvdb = if (cfg.tvdbKey.isBlank()) null else attempt { client.tvdb(121361, cfg, "en") }
            tvdb
        }
    }

    private fun readMiss(file: File): Boolean = runCatching {
        val o = JSONObject(file.readText())
        o.isNull("data") && o.optLong("expires") > System.currentTimeMillis()
    }.getOrDefault(false)

    private fun read(key: String): ExternalTitleMetadata? = runCatching {
        val o = JSONObject(AtomicFile(File(directory, key)).readFully().toString(Charsets.UTF_8))
        if (o.optLong("expires") <= System.currentTimeMillis()) return@runCatching null
        val d = o.optJSONObject("data") ?: return@runCatching null
        ExternalTitleMetadata(d.getInt("tmdb"), MetadataType.valueOf(d.getString("type")), d.getString("title"),
            tvdbId = d.optInt("tvdb").takeIf { it > 0 }, tmdbRating = ExternalMetadataRules.rating(d.optString("tmdbRating")),
            tvdbOverview = d.optString("overview").takeIf { it.isNotBlank() }, tvdbArtwork = d.optString("artwork").takeIf { it.isNotBlank() },
            tvdbUrl = d.optString("url").takeIf { it.startsWith("https://thetvdb.com/series/") }, tvdbLoaded = d.optBoolean("tvdbLoaded"),
            posterUrl = d.optString("posterUrl").takeIf { it.startsWith("https://") },
            logoUrl = d.optString("logoUrl").takeIf { it.startsWith("https://") },
            overview = d.optString("baseOverview").takeIf { it.isNotBlank() })
    }.getOrNull()

    private fun write(key: String, value: ExternalTitleMetadata?, ttl: Long) {
        // Disposable metadata cache: a full disk must not break playback or the EPG.
        runCatching {
            directory.mkdirs()
            directory.listFiles()?.filter { it.name != key }?.sortedByDescending { it.lastModified() }
                ?.drop(255)?.forEach { it.delete() }
            val data = value?.let { d -> JSONObject().put("tmdb", d.tmdbId).put("type", d.type.name).put("title", d.title)
                .put("tvdb", d.tvdbId).put("tmdbRating", d.tmdbRating)
                .put("overview", d.tvdbOverview).put("artwork", d.tvdbArtwork).put("url", d.tvdbUrl).put("tvdbLoaded", d.tvdbLoaded).put("posterUrl", d.posterUrl).put("logoUrl", d.logoUrl).put("baseOverview", d.overview) }
            val bytes = JSONObject().put("expires", System.currentTimeMillis() + ttl).put("data", data ?: JSONObject.NULL).toString().toByteArray()
            val atomic = AtomicFile(File(directory, key))
            val out = atomic.startWrite()
            try { out.write(bytes); atomic.finishWrite(out) } catch (error: Exception) { atomic.failWrite(out); throw error }
        }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    private companion object { const val HOUR = 3_600_000L; const val ARTWORK_CACHE_VERSION = 3 }
}
