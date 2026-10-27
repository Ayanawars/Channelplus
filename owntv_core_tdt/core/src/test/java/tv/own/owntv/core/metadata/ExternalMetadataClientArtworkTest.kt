package tv.own.owntv.core.metadata

import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ExternalMetadataClientArtworkTest {
    private fun client(events: MutableList<String>, translationCode: Int = 200) = ExternalMetadataClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            events.add(path)
            val body = when (path) {
                "/v4/login" -> """{"data":{"token":"test-token"}}"""
                "/v4/series/123/extended" -> """{"data":{"id":123,"slug":"my-series","overview":"Base summary","image":"https://example.com/poster.jpg","artworks":[{"type":7,"image":"https://example.com/logo.png","language":"spa","width":800}]}}"""
                "/v4/series/123/translations/spa" -> """{"data":{"overview":"Resumen"}}"""
                "/v4/artwork/types" -> """{"data":[{"id":7,"name":"Series ClearLogo"}]}"""
                else -> error(path)
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (path.endsWith("/translations/spa")) translationCode else 200).message("Test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build(),
    )

    @Test fun `poster is delivered before translation and logo requests finish`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val api = client(events)
        var partial: ExternalMetadataClient.TvdbDetails? = null
        val full = api.tvdb(123, ExternalMetadataConfig(tvdbKey = "test-key"), "es") {
            events.add("poster-ready"); partial = it
        }
        assertEquals("https://example.com/poster.jpg", partial?.artwork)
        assertNull(partial?.logo)
        assertTrue(events.indexOf("poster-ready") < events.indexOf("/v4/series/123/translations/spa"))
        assertTrue(events.indexOf("poster-ready") < events.indexOf("/v4/artwork/types"))
        assertEquals("Resumen", full.overview)
        assertEquals("https://example.com/logo.png", full.logo)
    }

    @Test fun `an optional translation failure does not erase the delivered poster`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val api = client(events, translationCode = 503)
        var partial: ExternalMetadataClient.TvdbDetails? = null
        val failure = runCatching {
            api.tvdb(123, ExternalMetadataConfig(tvdbKey = "test-key"), "es") { partial = it }
        }.exceptionOrNull()
        assertTrue(failure is ExternalMetadataClient.ApiFailure)
        assertEquals("https://example.com/poster.jpg", partial?.artwork)
    }
}
