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

    suspend fun live(rawTitle: String, details: EpgDetails?, onArtwork: (ExternalTitleMetadata) -> Unit = {}): ExternalTitleMetadata? = resolve(
        ExternalMetadataRules.liveTitle(rawTitle), details?.year, null, null, details, onArtwork,
    )

    suspend fun known(tmdbId: Int, type: MetadataType, onArtwork: (ExternalTitleMetadata) -> Unit = {}): ExternalTitleMetadata? {
        if (type == MetadataType.EPISODE || tmdbId <= 0) return null
        return resolve("", null, tmdbId, type, null, onArtwork)
    }

    private suspend fun resolve(title: String, year: Int?, knownId: Int?, type: MetadataType?, epg: EpgDetails?, onArtwork: (ExternalTitleMetadata) -> Unit): ExternalTitleMetadata? = withContext(Dispatchers.IO) {
        val cfg = store.config.value
        val metadata = settings.metadataConfig()
        if (!cfg.enabled || (knownId == null && title.length < 3)) return@withContext null
        val includeAdult = profileAllowsAdultMetadata(profiles.getById(settings.activeProfileIdNow())?.isKids)
        if (!includeAdult) return@withContext null
        if (cfg.traktClientId.isBlank() && cfg.tvdbKey.isBlank()) return@withContext null
        // Separate type, metadata language, maturity and credential configurations. No raw keys in filenames.
        val key = hash(listOf(ARTWORK_CACHE_VERSION, title, year, knownId, type, epg?.categories, epg?.episode, metadata.resolvedLanguage, includeAdult, cfg.json()).joinToString("|"))
        lock.withLock {
            read(key)?.let { return@withLock it }
            val file = File(directory, key)
            // A cached explicit miss is different from a failed network request.
            if (file.exists() && readMiss(file)) return@withLock null
            var failed = false
            var result: ExternalTitleMetadata? = null
            val categories = epg?.categoryList.orEmpty().joinToString(" ").lowercase(java.util.Locale.ROOT)
            val movieOnly = listOf("película", "pelicula", "movie", "film", "cine").any { categories.contains(it) }
            val tvOnly = !epg?.episode.isNullOrBlank() || categories.contains("serie")
            val target = type ?: when { tvOnly -> MetadataType.TV; movieOnly -> MetadataType.MOVIE; else -> null }
            fun choose(hits: List<ExternalMetadataClient.TitleHit>): ExternalMetadataClient.TitleHit? {
                val selected = ExternalMetadataRules.choose(title, if (target == MetadataType.TV) null else year,
                    hits.map { MetadataSearchResult(it.id, it.type, it.title, it.originalTitle, it.year, null, null, 0.0) })
                return hits.firstOrNull { it.id == selected?.tmdbId && it.type == selected?.type }
            }
            if (cfg.traktClientId.isNotBlank()) {
                try {
                    val hit = if (knownId != null) client.lookupTrakt(knownId, requireNotNull(type), cfg)
                        else choose(client.searchTrakt(title, if (target == MetadataType.TV) null else year, target, cfg))
                    if (hit != null) {
                        result = client.trakt(hit, cfg, metadata.resolvedLanguage)
                        onArtwork(requireNotNull(result))
                        try { result = result?.copy(imdbRating = client.imdbRating(hit.id, hit.type, cfg)) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            }
            if (cfg.tvdbKey.isNotBlank()) {
                try {
                    val base = result
                    val tvdbId = base?.tvdbId
                    val hit = if (tvdbId != null) ExternalMetadataClient.TitleHit(tvdbId, requireNotNull(base).type, base.title, base.year)
                        else if (base?.imdbId != null) client.searchTvdb(base.title, null, base.type, cfg, base.imdbId).singleOrNull()
                        else if (title.isNotBlank()) choose(client.searchTvdb(title, if (target == MetadataType.TV) null else year, target, cfg)) else null
                    if (hit != null) {
                        fun merge(data: ExternalMetadataClient.TvdbDetails): ExternalTitleMetadata =
                            (result ?: ExternalTitleMetadata(knownId ?: 0, hit.type, data.title ?: hit.title)).copy(
                                tvdbId = hit.id, tvdbOverview = data.overview, tvdbArtwork = data.artwork,
                                tvdbUrl = data.url, tvdbLoaded = true, posterUrl = data.artwork, logoUrl = data.logo,
                                overview = result?.overview ?: data.overview, year = result?.year ?: data.year ?: hit.year,
                                genres = result?.genres?.takeIf { it.isNotEmpty() } ?: data.genres, ageRating = data.ageRating)
                        val full = client.tvdb(hit.id, cfg, metadata.resolvedLanguage, { partial ->
                            result = merge(partial); onArtwork(requireNotNull(result))
                        }, hit.type)
                        result = merge(full)
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            }
            // Never replace a short-lived failure with a week-long negative cache entry.
            if (result == null && failed) return@withLock null
            coroutineContext.ensureActive()
            write(key, result, if (failed) 5 * 60_000L else if (result == null) 6 * HOUR else 7 * 24 * HOUR)
            result
        }
    }

    data class ServiceTest(val trakt: Boolean?, val tvdb: Boolean?)
    suspend fun test(): ServiceTest = withContext(Dispatchers.IO) {
        lock.withLock {
            val cfg = store.config.value
            suspend fun attempt(block: suspend () -> Unit): Boolean = try { block(); true }
                catch (error: CancellationException) { throw error } catch (_: Exception) { false }
            ServiceTest(if (cfg.traktClientId.isBlank()) null else attempt { client.testTrakt(cfg) },
                if (cfg.tvdbKey.isBlank()) null else attempt { client.tvdb(121361, cfg, "en") })
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
            tvdbId = d.optInt("tvdb").takeIf { it > 0 }, imdbRating = ExternalMetadataRules.rating(d.optString("imdbRating")),
            tvdbOverview = d.optString("overview").takeIf { it.isNotBlank() }, tvdbArtwork = d.optString("artwork").takeIf { it.isNotBlank() },
            tvdbUrl = d.optString("url").takeIf { it.startsWith("https://thetvdb.com/series/") || it.startsWith("https://thetvdb.com/movies/") }, tvdbLoaded = d.optBoolean("tvdbLoaded"),
            posterUrl = d.optString("posterUrl").takeIf { it.startsWith("https://") },
            logoUrl = d.optString("logoUrl").takeIf { it.startsWith("https://") },
            overview = d.optString("baseOverview").takeIf { it.isNotBlank() },
            traktId = d.optInt("traktId").takeIf { it > 0 }, imdbId = d.optString("imdbId").takeIf { it.startsWith("tt") },
            year = d.optInt("year").takeIf { it > 0 }, ageRating = d.optString("ageRating").takeIf { it.isNotBlank() },
            genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty())
    }.getOrNull()

    private fun write(key: String, value: ExternalTitleMetadata?, ttl: Long) {
        // Disposable metadata cache: a full disk must not break playback or the EPG.
        runCatching {
            directory.mkdirs()
            directory.listFiles()?.filter { it.name != key }?.sortedByDescending { it.lastModified() }
                ?.drop(255)?.forEach { it.delete() }
            val data = value?.let { d -> JSONObject().put("tmdb", d.tmdbId).put("type", d.type.name).put("title", d.title)
                .put("tvdb", d.tvdbId).put("imdbRating", d.imdbRating)
                .put("traktId", d.traktId).put("imdbId", d.imdbId).put("year", d.year).put("ageRating", d.ageRating).put("genres", org.json.JSONArray(d.genres))
                .put("overview", d.tvdbOverview).put("artwork", d.tvdbArtwork).put("url", d.tvdbUrl).put("tvdbLoaded", d.tvdbLoaded).put("posterUrl", d.posterUrl).put("logoUrl", d.logoUrl).put("baseOverview", d.overview) }
            val bytes = JSONObject().put("expires", System.currentTimeMillis() + ttl).put("data", data ?: JSONObject.NULL).toString().toByteArray()
            val atomic = AtomicFile(File(directory, key))
            val out = atomic.startWrite()
            try { out.write(bytes); atomic.finishWrite(out) } catch (error: Exception) { atomic.failWrite(out); throw error }
        }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    private companion object { const val HOUR = 3_600_000L; const val ARTWORK_CACHE_VERSION = 4 }
}
