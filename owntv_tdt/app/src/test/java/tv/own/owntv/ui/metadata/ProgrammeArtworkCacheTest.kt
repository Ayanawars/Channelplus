package tv.own.owntv.ui.metadata

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.ExternalTitleMetadata
import tv.own.owntv.core.metadata.MetadataType

class ProgrammeArtworkCacheTest {
    private val poster = ExternalTitleMetadata(1, MetadataType.TV, "Programme", posterUrl = "https://example.com/poster.jpg")

    @Test fun `reopening the same programme retains its poster and later logo`() {
        val cache = ProgrammeArtworkCache<String>()
        cache.put("airing", poster)
        assertEquals(poster, cache.get("airing"))
        val enriched = poster.copy(logoUrl = "https://example.com/logo.png")
        cache.put("airing", enriched)
        assertEquals(enriched, cache.get("airing"))
    }

    @Test fun `different profiles configuration and airings never inherit artwork`() {
        val cache = ProgrammeArtworkCache<List<Any>>()
        cache.put(listOf(1L, "enabled", "first-airing"), poster)
        assertNull(cache.get(listOf(2L, "enabled", "first-airing")))
        assertNull(cache.get(listOf(1L, "disabled", "first-airing")))
        assertNull(cache.get(listOf(1L, "enabled", "second-airing")))
    }

    @Test fun `expiry and LRU bounds prevent an unbounded stale image cache`() {
        var now = 0L
        val cache = ProgrammeArtworkCache<String>(capacity = 2, ttlMs = 100, now = { now })
        cache.put("first", poster); cache.put("second", poster)
        assertNotNull(cache.get("first"))
        cache.put("third", poster)
        assertNull(cache.get("second"))
        assertNotNull(cache.get("first"))
        now = 100
        assertNull(cache.get("first"))
        assertNull(cache.get("third"))
    }
}
