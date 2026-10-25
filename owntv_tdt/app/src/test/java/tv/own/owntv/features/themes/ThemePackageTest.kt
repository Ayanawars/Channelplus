package tv.own.owntv.features.themes

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ThemePackageTest {
    private val root: File get() = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "themes/catalog.json").isFile }
    private fun cyber() = JSONObject(File(root, "themes/v1/cyberpunk.json").readText())
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid remote theme was accepted") } catch (_: Exception) { }
    }
    @Test fun publishedPackagesMatchCatalogAndChecksums() {
        val catalog = parseCatalog(File(root, "themes/catalog.json").readText())
        assertEquals(3, catalog.size)
        catalog.forEach { entry ->
            val bytes = File(root, "themes/v1/${entry.id}.json").readBytes()
            assertEquals(entry.asset.bytes, bytes.size)
            assertEquals(entry.asset.sha256, digest(bytes))
            val theme = ThemePackage.parse(bytes.toString(Charsets.UTF_8))
            assertEquals(entry.id, theme.id)
            assertEquals(entry.name, theme.name)
            assertEquals(entry.version, theme.version)
            assertEquals(entry.light, theme.light)
            assertEquals(entry.totalBytes, bytes.size + (theme.wallpaper?.bytes ?: 0))
            theme.wallpaper?.let {
                val wallpaper = File(root, "themes/v1/cyberpunk.jpg").readBytes()
                assertEquals(it.bytes, wallpaper.size)
                assertEquals(it.sha256, digest(wallpaper))
            }
        }
    }
    @Test fun oledHasPureBlackBackgroundAndColoredAccents() {
        val theme = ThemePackage.parse(File(root, "themes/v1/oled-esmeralda.json").readText())
        assertEquals(0xFF000000L, theme.colors.getValue("page"))
        assertNotEquals(0xFFFFFFFFL, theme.colors.getValue("accent"))
        assertNull(theme.wallpaper)
    }
    @Test fun rejectsPathTraversal() { rejects { safeId("../escape") } }
    @Test fun rejectsUnsupportedSchema() { rejects { ThemePackage.parse(cyber().put("schema", 2).toString()) } }
    @Test fun rejectsMissingPaletteRole() { rejects {
        val json = cyber(); json.getJSONObject("colors").remove("text"); ThemePackage.parse(json.toString())
    } }
    @Test fun rejectsUnreadableText() { rejects {
        val json = cyber(); json.getJSONObject("colors").put("text", "#182139"); ThemePackage.parse(json.toString())
    } }
    @Test fun rejectsUntrustedAssets() { rejects {
        val json = cyber(); json.getJSONObject("wallpaper").put("url", "https://example.com/theme.png")
        ThemePackage.parse(json.toString())
    } }
    @Test fun rejectsOversizedAssets() { rejects {
        val json = cyber(); json.getJSONObject("wallpaper").put("bytes", MAX_IMAGE + 1)
        ThemePackage.parse(json.toString())
    } }
    @Test fun rejectsInvalidChecksum() { rejects {
        val json = cyber(); json.getJSONObject("wallpaper").put("sha256", "not-a-checksum")
        ThemePackage.parse(json.toString())
    } }
    @Test fun rejectsDuplicateCatalogIds() { rejects {
        val json = JSONObject(File(root, "themes/catalog.json").readText())
        val items = json.getJSONArray("themes"); items.put(items.getJSONObject(0)); parseCatalog(json.toString())
    } }
    @Test fun rejectsNonOpaqueColors() { rejects { color("#00FFFFFF") } }
}
