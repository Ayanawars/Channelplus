package tv.own.owntv.core.metadata

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Official TheTVDB v4 adapter. No HTML scraping or embedded credentials. */
class ExternalMetadataClient(client: OkHttpClient) {
    // Never redirect a credential-bearing call to another host. Cancellation closes the HTTP call.
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private var token: String? = null
    private var tokenKey: Pair<String, String>? = null
    private var tokenAt = 0L
    private var artworkTypes: Map<Int, String>? = null

    class ApiFailure(val status: Int) : IOException()

    fun clearSession() { token = null; tokenKey = null; tokenAt = 0L }

    data class TvdbDetails(val overview: String?, val artwork: String?, val url: String?, val logo: String?,
        val title: String? = null, val year: Int? = null, val genres: List<String> = emptyList(), val ageRating: String? = null)

    suspend fun tvdb(id: Int, config: ExternalMetadataConfig, language: String, onArtwork: (TvdbDetails) -> Unit = {}, type: MetadataType = MetadataType.TV): TvdbDetails {
        require(id > 0)
        val entity = if (type == MetadataType.MOVIE) "movies" else "series"
        val data = authorized("/$entity/$id/extended", config).optJSONObject("data") ?: throw ApiFailure(422)
        if (data.optInt("id") != id) throw ApiFailure(422)
        var overview = clean(data.optString("overview"))
        val artwork = clean(data.optString("image"))?.takeIf { it.startsWith("https://") }
        val slug = data.optString("slug").takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) }
        val url = slug?.let { "https://thetvdb.com/$entity/$it" }
        val title = clean(data.optString("name"))
        val year = clean(data.optString(if (type == MetadataType.MOVIE) "year" else "firstAired"))?.take(4)?.toIntOrNull()
        val genres = data.optJSONArray("genres").objects().mapNotNull { clean(it.optString("name")) }
        val age = data.optJSONArray("contentRatings").objects().firstOrNull { it.optString("country").lowercase() in setOf("esp", "es", "spain") }?.optString("name")?.let(::clean)
        onArtwork(TvdbDetails(overview, artwork, url, null, title, year, genres, age))
        val iso3 = runCatching { java.util.Locale.forLanguageTag(language.ifBlank { "en" }).isO3Language }.getOrNull()
        if (!iso3.isNullOrBlank() && iso3 != "eng") {
            // A missing translation keeps the base overview; auth/network errors propagate for retry.
            try {
                val translated = authorized("/$entity/$id/translations/$iso3", config).optJSONObject("data")
                overview = translated?.optString("overview")?.let(::clean) ?: overview
            } catch (error: ApiFailure) { if (error.status != 404) throw error }
        }
        return TvdbDetails(overview, artwork, url, clearLogo(data, config, iso3), title, year, genres, age)
    }

    data class TitleHit(val id: Int, val type: MetadataType, val title: String, val year: Int?, val originalTitle: String? = null, val tmdbId: Int? = null, val overview: String? = null)

    suspend fun searchTrakt(title: String, year: Int?, type: MetadataType?, config: ExternalMetadataConfig): List<TitleHit> {
        val kind = when (type) { MetadataType.TV -> "show"; MetadataType.MOVIE -> "movie"; else -> "movie,show" }
        val url = (TRAKT + "/search/$kind").toHttpUrl().newBuilder()
            .addQueryParameter("query", title).addQueryParameter("fields", "title,aliases,translations")
            .addQueryParameter("limit", "10").addQueryParameter("extended", "full")
        year?.let { url.addQueryParameter("years", it.toString()) }
        val hits = array(traktRequest(url.build().toString(), config)).objects().mapNotNull { row ->
            val tv = row.optString("type") == "show"
            val item = row.optJSONObject(if (tv) "show" else "movie") ?: return@mapNotNull null
            val id = item.optJSONObject("ids")?.optInt("trakt")?.takeIf { it > 0 } ?: return@mapNotNull null
            TitleHit(id, if (tv) MetadataType.TV else MetadataType.MOVIE, item.getString("title"), item.optInt("year").takeIf { it > 0 },
                tmdbId = item.optJSONObject("ids")?.optInt("tmdb")?.takeIf { it > 0 }, overview = clean(item.optString("overview")))
        }
        if (hits.any { TitleMatchScorer.score(title, null, it.title, null, null) >= 0.9 }) return hits
        // A translated EPG title must match a real provider alias, never the first search result.
        return hits.take(5).map { hit ->
            val entity = if (hit.type == MetadataType.TV) "shows" else "movies"
            val aliases = array(traktRequest("$TRAKT/$entity/${hit.id}/aliases", config)).objects()
            val alias = aliases.mapNotNull { clean(it.optString("title")) }
                .firstOrNull { TitleMatchScorer.score(title, null, it, null, null) >= 0.9 }
            if (alias != null) hit.copy(title = alias, originalTitle = hit.title) else hit
        }
    }

    /** TMDB IDs are accepted only as cross-reference identifiers, never queried on TMDB. */
    suspend fun lookupTrakt(tmdbId: Int, type: MetadataType, config: ExternalMetadataConfig): TitleHit? {
        val kind = if (type == MetadataType.TV) "show" else "movie"
        val rows = array(traktRequest("$TRAKT/search/tmdb/$tmdbId?type=$kind", config)).objects()
        val hits = rows.mapNotNull { row -> row.optJSONObject(kind)?.let { item ->
            item.optJSONObject("ids")?.optInt("trakt")?.takeIf { it > 0 }?.let {
                TitleHit(it, type, item.getString("title"), item.optInt("year").takeIf { n -> n > 0 })
            }
        } }.distinctBy { it.id }
        return hits.singleOrNull()
    }

    suspend fun trakt(hit: TitleHit, config: ExternalMetadataConfig, language: String): ExternalTitleMetadata {
        val entity = if (hit.type == MetadataType.TV) "shows" else "movies"
        val base = "$TRAKT/$entity/${hit.id}"
        val data = json(traktRequest("$base?extended=full", config))
        val ids = data.optJSONObject("ids") ?: throw ApiFailure(422)
        if (ids.optInt("trakt") != hit.id) throw ApiFailure(422)
        var overview = clean(data.optString("overview"))
        val lang = java.util.Locale.forLanguageTag(language).language
        if (lang.isNotBlank() && lang != "en") {
            try {
                val translations = array(traktRequest("$base/translations?language=$lang", config)).objects()
                overview = translations.firstOrNull { it.optString("language") == lang }?.optString("overview")?.let(::clean) ?: overview
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { /* Base metadata survives an optional translation failure. */ }
        }
        return ExternalTitleMetadata(ids.optInt("tmdb"), hit.type, data.getString("title"),
            tvdbId = ids.optInt("tvdb").takeIf { it > 0 }, traktId = hit.id,
            imdbId = clean(ids.optString("imdb")), overview = overview, year = data.optInt("year").takeIf { it > 0 },
            genres = data.optJSONArray("genres").strings())
    }

    /** Never put Trakt's own community score beneath an IMDb logo. */
    suspend fun imdbRating(id: Int, type: MetadataType, config: ExternalMetadataConfig): Double? {
        val entity = if (type == MetadataType.TV) "shows" else "movies"
        val data = json(traktRequest("$TRAKT/$entity/$id/ratings?extended=all", config))
        return ExternalMetadataRules.rating(data.optJSONObject("imdb")?.optString("rating"))
    }

    suspend fun searchTvdb(title: String, year: Int?, type: MetadataType?, config: ExternalMetadataConfig, imdbId: String? = null): List<TitleHit> {
        val url = (TVDB + "/search").toHttpUrl().newBuilder()
            .addQueryParameter("query", title).addQueryParameter("limit", "10")
        if (type != null) url.addQueryParameter("type", if (type == MetadataType.TV) "series" else "movie")
        year?.let { url.addQueryParameter("year", it.toString()) }
        imdbId?.let { url.addQueryParameter("remote_id", it) }
        val path = url.build().let { it.encodedPath.removePrefix("/v4") + "?" + it.encodedQuery }
        return authorized(path, config).optJSONArray("data").objects().mapNotNull { row ->
            val kind = when (row.optString("type")) { "series" -> MetadataType.TV; "movie" -> MetadataType.MOVIE; else -> return@mapNotNull null }
            val id = row.optString("tvdb_id").toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val translated = row.optJSONObject("translations")?.optString("spa")?.let(::clean)
            TitleHit(id, kind, translated ?: row.getString("name"), row.optString("year").toIntOrNull(), row.optString("name"))
        }
    }

    internal suspend fun traktArray(path: String, config: ExternalMetadataConfig): JSONArray = array(traktRequest(TRAKT + path, config))

    suspend fun testTrakt(config: ExternalMetadataConfig) { json(traktRequest("$TRAKT/movies/1", config)) }

    private fun traktRequest(url: String, config: ExternalMetadataConfig): Request = Request.Builder().url(url)
        .header("trakt-api-version", "2").header("trakt-api-key", config.traktClientId)
        .header("Content-Type", "application/json").build()

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { clean(optString(it)) }

    /** Resolve artwork type IDs from the API rather than hardcoding a provider-specific number. */
    private suspend fun clearLogo(data: JSONObject, config: ExternalMetadataConfig, language: String?): String? {
        val artwork = data.optJSONArray("artworks") ?: return null
        val types = artworkTypes ?: run {
            val rows = authorized("/artwork/types", config).optJSONArray("data") ?: throw ApiFailure(422)
            (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val id = row.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
                id to row.optString("name")
            }.toMap().also { artworkTypes = it }
        }
        val candidates = (0 until artwork.length()).mapNotNull { i ->
            val art = artwork.optJSONObject(i) ?: return@mapNotNull null
            val name = types[art.optInt("type")]?.lowercase(java.util.Locale.ROOT)?.replace(" ", "") ?: return@mapNotNull null
            // Icons can be square posters. Only a ClearLogo is safe in the transparent title slot.
            if (!name.contains("clearlogo")) return@mapNotNull null
            val url = clean(art.optString("image"))?.takeIf { it.startsWith("https://") && !it.endsWith(".svg", true) }
                ?: return@mapNotNull null
            val lang = art.optString("language")
            val rank = when { language != null && lang == language -> 0; lang == "eng" -> 1; lang.isBlank() || lang == "null" -> 2; else -> 3 }
            Triple(url, rank, art.optInt("width"))
        }
        return candidates.minWithOrNull(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })?.first
    }

    /** Serialized by the repository. Re-authenticate once on expiry, never loop on a bad PIN. */
    private suspend fun authorized(path: String, config: ExternalMetadataConfig): JSONObject {
        suspend fun login(): String {
            val payload = JSONObject().put("apikey", config.tvdbKey)
            if (config.tvdbPin.isNotBlank()) payload.put("pin", config.tvdbPin)
            val result = json(Request.Builder().url(TVDB + "/login")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build())
            return result.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                ?.also { token = it; tokenAt = System.currentTimeMillis(); tokenKey = config.tvdbKey to config.tvdbPin }
                ?: throw ApiFailure(401)
        }
        var bearer = token?.takeIf { tokenKey == (config.tvdbKey to config.tvdbPin) && System.currentTimeMillis() - tokenAt < 25L * 86_400_000 }
            ?: login()
        suspend fun get() = json(Request.Builder().url(TVDB + path).header("Authorization", "Bearer $bearer").build())
        return try { get() } catch (error: ApiFailure) {
            if (error.status != 401) throw error
            token = null
            bearer = login()
            get()
        }
    }

    /** Never propagate credentials or provider response bodies in errors. */
    private suspend fun json(request: Request): JSONObject = JSONObject(body(request))
    private suspend fun array(request: Request): JSONArray = JSONArray(body(request))
    private suspend fun body(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(ApiFailure(0))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) throw ApiFailure(response.code)
                        val body = response.body
                        if (body.contentLength() > 2_000_000) throw ApiFailure(413)
                        val buffer = java.io.ByteArrayOutputStream()
                        val input = body.byteStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            if (buffer.size() + n > 2_000_000) throw ApiFailure(413)
                            buffer.write(chunk, 0, n)
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it as? ApiFailure ?: ApiFailure(422)) },
                    )
                }
            }
        })
    }

    private fun clean(s: String): String? = s.trim().takeIf { it.isNotEmpty() && it != "null" && it != "N/A" }
    private companion object { const val TRAKT = "https://api.trakt.tv"; const val TVDB = "https://api4.thetvdb.com/v4" }
}
