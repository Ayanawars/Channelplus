package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test

class ExternalMetadataRulesTest {
    private fun hit(id: Int, title: String, year: Int?, type: MetadataType = MetadataType.MOVIE) =
        MetadataSearchResult(id, type, title, null, year, null, null, 0.0)

    @Test fun `unknown unavailable and nonfinite scores never become ratings`() {
        listOf(null, "N/A", "NaN", "Infinity", "0", "-1", "11").forEach { assertNull(ExternalMetadataRules.rating(it)) }
        assertEquals(8.7, ExternalMetadataRules.rating("8.7")!!, 0.001)
    }

    @Test fun `remakes without a year are ambiguous`() {
        assertNull(ExternalMetadataRules.choose("Dune", null, listOf(hit(1, "Dune", 1984), hit(2, "Dune", 2021))))
        assertEquals(2, ExternalMetadataRules.choose("Dune", 2021, listOf(hit(1, "Dune", 1984), hit(2, "Dune", 2021)))?.tmdbId)
    }

    @Test fun `equally named movie and series never use the first result blindly`() {
        assertNull(ExternalMetadataRules.choose("Fargo", null, listOf(hit(1, "Fargo", 1996), hit(2, "Fargo", 2014, MetadataType.TV))))
    }

    @Test fun `partial or unrelated titles cannot acquire somebody elses ratings`() {
        assertNull(ExternalMetadataRules.choose("Noticias", null, listOf(hit(1, "Noticias del mundo", 2020))))
        assertNull(ExternalMetadataRules.choose("MasterChef", null, listOf(hit(1, "MasterChef Celebrity", null, MetadataType.TV))))
    }

    @Test fun `live cleanup preserves titles with numbers and colons`() {
        assertEquals("Dune", ExternalMetadataRules.liveTitle("Cine: Dune"))
        assertEquals("MasterChef Celebrity", ExternalMetadataRules.liveTitle("MasterChef Celebrity T1"))
        assertEquals("1917", ExternalMetadataRules.liveTitle("1917"))
        assertEquals("CSI: Miami", ExternalMetadataRules.liveTitle("CSI: Miami"))
    }

}
