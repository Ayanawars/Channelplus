package tv.own.owntv.core.epg

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PiconCatalogTest {
    private fun catalog() = File("src/main/assets/picons/catalog.tsv").bufferedReader().use {
        PiconCatalog.fromTsv(it.lineSequence())
    }

    @Test fun `quality variants and Spanish names resolve without a network`() {
        val catalog = catalog()
        for (name in listOf("La 1", "LA 1 HD", "[ES] Telecinco FHD", "Antena 3 4K", "Clan", "TDP", "National Geographic", "Movistar Deportes 2 HD", "M+ LCAMPEON2")) {
            for (style in listOf(PiconStyle.TRANSPARENT, PiconStyle.COLOR)) {
                val url = catalog.iconFor(name, style)
                assertNotNull("$name / $style", url)
                assertTrue(File("src/main/assets/" + url!!.removePrefix("file:///android_asset/")).isFile)
            }
        }
    }

    @Test fun `numbered sports feeds stay distinct and unknown names fall back`() {
        val catalog = catalog()
        assertNotEquals(catalog.iconFor("DAZN 1", PiconStyle.COLOR), catalog.iconFor("DAZN 2", PiconStyle.COLOR))
        assertNull(catalog.iconFor("DAZN 999", PiconStyle.COLOR))
        assertNull(catalog.iconFor("Telecinco +1", PiconStyle.COLOR))
        assertNull(catalog.iconFor("An unknown channel", PiconStyle.TRANSPARENT))
        assertNull(catalog.iconFor("La 1", PiconStyle.ORIGINAL))
    }

    @Test fun `style changes choose different assets without changing channel data`() {
        val catalog = catalog()
        val transparent = catalog.iconFor("La 1", PiconStyle.TRANSPARENT)
        val color = catalog.iconFor("La 1", PiconStyle.COLOR)
        assertTrue(transparent!!.contains("/transparent/"))
        assertTrue(color!!.contains("/color/"))
        assertEquals(PiconStyle.ORIGINAL, PiconStyle.fromStored("invalid-backup-value"))
    }

    @Test fun `malformed catalog paths cannot escape the bundled pack`() {
        val catalog = PiconCatalog.fromTsv(sequenceOf("Evil\t../../secrets.png\thttps://example.com/logo.png"))
        assertNull(catalog.iconFor("Evil", PiconStyle.COLOR))
        assertNull(catalog.iconFor("Evil", PiconStyle.TRANSPARENT))
    }
}
