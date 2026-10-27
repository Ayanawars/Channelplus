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

    data class TvdbDetails(val overview: String?, val artwork: String?, val url: String?, val logo: String?)

    suspend fun tvdb(id: Int, config: ExternalMetadataConfig, language: String, onArtwork: (TvdbDetails) -> Unit = {}): TvdbDetails {
        require(id > 0)
        val data = authorized("/series/$id/extended", config).optJSONObject("data") ?: throw ApiFailure(422)
        if (data.optInt("id") != id) throw ApiFailure(422)
        var overview = clean(data.optString("overview"))
        val artwork = clean(data.optString("image"))?.takeIf { it.startsWith("https://") }
        val slug = data.optString("slug").takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) }
        val url = slug?.let { "https://thetvdb.com/series/$it" }
        onArtwork(TvdbDetails(overview, artwork, url, null))
        val iso3 = runCatching { java.util.Locale.forLanguageTag(language.ifBlank { "en" }).isO3Language }.getOrNull()
        if (!iso3.isNullOrBlank() && iso3 != "eng") {
            // A missing translation keeps the base overview; auth/network errors propagate for retry.
            try {
                val translated = authorized("/series/$id/translations/$iso3", config).optJSONObject("data")
                overview = translated?.optString("overview")?.let(::clean) ?: overview
            } catch (error: ApiFailure) { if (error.status != 404) throw error }
        }
        return TvdbDetails(overview, artwork, url, clearLogo(data, config, iso3))
    }

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
    private suspend fun json(request: Request): JSONObject = suspendCancellableCoroutine { continuation ->
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
                        JSONObject(buffer.toString(Charsets.UTF_8.name()))
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
    private companion object { const val TVDB = "https://api4.thetvdb.com/v4" }
}
