package tv.own.owntv.core.live

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.TifChannelUri

class ChannelVariantsTest {
    private fun channel(id: Long, name: String, tdt: Boolean = false, height: Int? = null) =
        ChannelEntity(id = id, sourceId = if (tdt) 1 else 2, name = name,
            streamUrl = if (tdt) TifChannelUri("tv.input/service", id, height).encode() else "https://example.test/$id")

    @Test fun allTdtVersionsPrecedeEvenUhdIptv() {
        val rows = listOf(channel(1, "La 1 FHD"), channel(2, "La1 SD", true),
            channel(3, "La 1 UHD"), channel(4, "La 1 HD", true), channel(5, "La1 FHD", true))
        assertEquals(listOf(5L, 4L, 2L, 3L, 1L), ChannelVariants.variants(rows, rows[0]).map { it.id })
        assertEquals(1, ChannelVariants.collapse(rows).size)
        assertEquals("La1", ChannelVariants.collapse(rows).single().name)
    }
    @Test fun iptvOnlyStartsAtBestQuality() {
        val rows = listOf(channel(1, "La 1"), channel(2, "La 1 HD"), channel(3, "La1 FHD"))
        assertEquals(listOf(3L, 2L, 1L), ChannelVariants.variants(rows, rows[0]).map { it.id })
    }
    @Test fun regionalFeedsAndCountriesNeverMergeEvenWithSharedEpgId() {
        val rows = listOf(channel(1, "La 1 HD"), channel(2, "La 1 Canarias FHD"),
            channel(3, "FR| La 1 FHD"), channel(4, "La 1 +1 HD"))
            .map { it.copy(epgChannelId = "shared") }
        assertEquals(4, ChannelVariants.collapse(rows).size)
        assertEquals(4, ChannelVariants.collapse(ChannelVariants.collapse(rows)).size)
    }
    @Test fun spanishPrefixesAndProviderSuperscriptsMatchTuner() {
        val rows = listOf(channel(1, "La 1", true), channel(2, "ES| La1 ᶠᴴᴰ"), channel(3, "ESP| La 1 [HD] IPTV"))
        assertEquals(1, ChannelVariants.collapse(rows).size)
        assertEquals("FHD", ChannelVariants.qualityLabel(rows[1]))
    }
    @Test fun actualTunerFormatWinsOverMisleadingName() {
        val ch = channel(1, "La 1 HD", true, 1080)
        assertEquals("FHD", ChannelVariants.qualityLabel(ch))
        assertNull(ChannelVariants.qualityLabel(channel(2, "La 1", true)))
    }
    @Test fun collapsePreservesStationOrderAndDoesNotMutateSourceRows() {
        val rows = listOf(channel(1, "La 2 HD"), channel(2, "La 1 SD"), channel(3, "La 2 FHD"))
        val collapsed = ChannelVariants.collapse(rows)
        assertEquals(listOf(3L, 2L), collapsed.map { it.id })
        assertEquals("La 2 HD", rows[0].name)
        assertEquals("https://example.test/3", collapsed[0].streamUrl)
    }
}
