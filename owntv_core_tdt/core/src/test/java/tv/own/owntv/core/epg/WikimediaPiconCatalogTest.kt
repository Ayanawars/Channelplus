package tv.own.owntv.core.epg

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WikimediaPiconCatalogTest {
    private val assets = File("src/main/assets/channel-logos/wikimedia")
    private fun catalog() = assets.resolve("catalog.tsv").bufferedReader().use {
        PiconCatalog.fromWikimediaTsv(it.lineSequence())
    }

    @Test fun `Spanish and regional aliases resolve to bundled SVGs`() {
        val catalog = catalog()
        for (name in listOf("La 1 FHD", "TVE 1", "[ES] Telecinco HD", "A3Series", "Clan", "TDP",
            "À Punt HD", "TV3", "3/24", "ETB 2 HD", "Aragón TV", "TVG")) {
            val url = catalog.iconFor(name, PiconStyle.WIKIMEDIA)
            assertNotNull(name, url)
            assertTrue(url!!.startsWith("file:///android_asset/channel-logos/wikimedia/"))
            assertTrue(assets.resolve(url.substringAfterLast('/')).isFile)
        }
    }

    @Test fun `exact UHD editions and numbered channels remain distinct`() {
        val catalog = catalog()
        assertNotEquals(catalog.iconFor("La 1", PiconStyle.WIKIMEDIA), catalog.iconFor("La 1 UHD", PiconStyle.WIKIMEDIA))
        assertNotEquals(catalog.iconFor("ETB 1", PiconStyle.WIKIMEDIA), catalog.iconFor("ETB 2", PiconStyle.WIKIMEDIA))
        assertNull(catalog.iconFor("ETB 12", PiconStyle.WIKIMEDIA))
        assertNull(catalog.iconFor("Telecinco +1", PiconStyle.WIKIMEDIA))
        assertNull(catalog.iconFor("Unknown TV", PiconStyle.WIKIMEDIA))
    }

    @Test fun `all indexed assets exist and the new style persists without changing old styles`() {
        val catalog = catalog()
        val rows = assets.resolve("catalog.tsv").readLines().filter(String::isNotBlank)
        assertTrue(rows.size > 100)
        for (row in rows) {
            val (name, path) = row.split('\t')
            assertTrue(assets.resolve(path).isFile)
            assertEquals(name, "file:///android_asset/channel-logos/wikimedia/$path", catalog.iconFor(name, PiconStyle.WIKIMEDIA))
        }
        assertEquals(PiconStyle.WIKIMEDIA, PiconStyle.fromStored(null))
        assertEquals(PiconStyle.ORIGINAL, PiconStyle.fromStored("ORIGINAL"))
        assertEquals(PiconStyle.WIKIMEDIA, PiconStyle.fromStored("WIKIMEDIA"))
        assertEquals(PiconStyle.COLOR, PiconStyle.fromStored("COLOR"))
        assertNull(catalog.iconFor("La 1", PiconStyle.ORIGINAL))
        assertNull(catalog.iconFor("La 1", PiconStyle.COLOR))
    }

    @Test fun `the SVG catalog rejects absolute paths URLs and directory traversal`() {
        val catalog = PiconCatalog.fromWikimediaTsv(sequenceOf(
            "Bad 1\t../../secrets.svg", "Bad 2\thttps://example.com/logo.svg",
            "Bad 3\t/absolute/logo.svg", "Bad 4\tlogo.svg", "Bad 5\t012345678901234567890123.png",
        ))
        for (i in 1..5) assertNull(catalog.iconFor("Bad $i", PiconStyle.WIKIMEDIA))
    }
}
