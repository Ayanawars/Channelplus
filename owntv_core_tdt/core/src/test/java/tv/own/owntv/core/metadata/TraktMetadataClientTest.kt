package tv.own.owntv.core.metadata

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TraktMetadataClientTest {
    private fun api(body: String, status: Int = 200) = ExternalMetadataClient(OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        assertEquals("api.trakt.tv", request.url.host)
        assertEquals("test-client", request.header("trakt-api-key"))
        assertEquals("2", request.header("trakt-api-version"))
        assertEquals("all", request.url.queryParameter("extended"))
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private val cfg = ExternalMetadataConfig(traktClientId = "test-client")

    @Test fun `only the explicitly named IMDb score can appear beneath its logo`() = runBlocking {
        val data = """{"rating":9.5,"trakt":{"rating":9.5},"tmdb":{"rating":9.1},"imdb":{"rating":8.4}}"""
        assertEquals(8.4, api(data).imdbRating(1, MetadataType.TV, cfg)!!, 0.001)
    }
    @Test fun `missing or invalid IMDb score never falls back to Trakt or TMDB`() = runBlocking {
        listOf("""{"rating":8.4}""", """{"imdb":null,"trakt":{"rating":8.4}}""",
            """{"imdb":{"rating":0}}""", """{"imdb":{"rating":11}}""").forEach {
            assertNull(api(it).imdbRating(1, MetadataType.MOVIE, cfg))
        }
    }
    @Test fun `authentication errors retain their retryable failure instead of a false rating`() = runBlocking {
        val failure = runCatching { api("{}", 401).imdbRating(1, MetadataType.MOVIE, cfg) }.exceptionOrNull()
        assertEquals(401, (failure as ExternalMetadataClient.ApiFailure).status)
    }
}
