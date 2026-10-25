package tv.own.owntv.features.themes

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/** Data only: theme downloads cannot contain code, layouts, fonts or executable archives. */
data class ThemeAsset(val url: String, val bytes: Int, val sha256: String) {
    companion object {
        fun parse(o: JSONObject, limit: Int): ThemeAsset {
            val url = o.getString("url")
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "raw.githubusercontent.com")
            require(uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port == 443))
            require(uri.path.startsWith("/Ayanawars/Channelplus/"))
            val bytes = o.getInt("bytes").also { require(it in 1..limit) }
            val sha = o.getString("sha256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            return ThemeAsset(url, bytes, sha)
        }
    }
}

data class ThemeEntry(val id: String, val name: String, val version: Int, val light: Boolean,
    val preview: List<Long>, val asset: ThemeAsset, val totalBytes: Int) {
    companion object {
        fun parse(o: JSONObject): ThemeEntry {
            val id = safeId(o.getString("id"))
            val name = o.getString("name").also { require(it.length in 1..48) }
            val version = o.getInt("version").also { require(it > 0) }
            val preview = o.getJSONArray("preview").also { require(it.length() == 3) }
            return ThemeEntry(id, name, version, o.getBoolean("light"),
                (0..2).map { color(preview.getString(it)) },
                ThemeAsset.parse(o.getJSONObject("package"), MAX_PACKAGE),
                o.getInt("totalBytes").also { require(it in 1..(MAX_IMAGE + MAX_PACKAGE)) })
        }
    }
}

const val MAX_PACKAGE = 32 * 1024
const val MAX_IMAGE = 4 * 1024 * 1024
fun safeId(id: String): String = id.also { require(it.matches(Regex("[a-z][a-z0-9-]{0,39}"))) }
fun color(hex: String): Long {
    require(hex.matches(Regex("#[0-9a-fA-F]{6}")))
    return 0xFF000000L or hex.drop(1).toLong(16)
}
fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

val THEME_ROLES = setOf("page", "surface", "surfaceHigh", "text", "muted", "accent", "onAccent", "secondary", "tertiary", "focus", "outline")
data class ThemePackage(val id: String, val name: String, val version: Int, val light: Boolean,
    val colors: Map<String, Long>, val wallpaper: ThemeAsset?) {
    companion object {
        fun parse(text: String): ThemePackage {
            require(text.toByteArray().size <= MAX_PACKAGE)
            val o = JSONObject(text)
            require(o.getInt("schema") == 1)
            val colors = o.getJSONObject("colors")
            require(colors.keys().asSequence().toSet() == THEME_ROLES)
            val palette = THEME_ROLES.associateWith { color(colors.getString(it)) }
            // Refuse unreadable labels, focused controls and body copy in remotely supplied palettes.
            require(contrast(palette.getValue("text"), palette.getValue("surfaceHigh")) >= 4.5)
            require(contrast(palette.getValue("muted"), palette.getValue("surfaceHigh")) >= 4.5)
            require(contrast(palette.getValue("text"), palette.getValue("page")) >= 4.5)
            require(contrast(palette.getValue("text"), palette.getValue("surface")) >= 4.5)
            require(contrast(palette.getValue("muted"), palette.getValue("surface")) >= 4.5)
            require(contrast(palette.getValue("accent"), palette.getValue("onAccent")) >= 4.5)
            val version = o.getInt("version").also { require(it > 0) }
            val name = o.getString("name").also { require(it.length in 1..48) }
            return ThemePackage(safeId(o.getString("id")), name, version, o.getBoolean("light"), palette,
                o.optJSONObject("wallpaper")?.let { ThemeAsset.parse(it, MAX_IMAGE) })
        }
    }
}
fun contrast(a: Long, b: Long): Double {
    fun luminance(c: Long): Double {
        fun channel(shift: Int): Double {
            val s = ((c shr shift) and 255) / 255.0
            return if (s <= .04045) s / 12.92 else Math.pow((s + .055) / 1.055, 2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    val x = luminance(a); val y = luminance(b)
    return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
}
fun parseCatalog(text: String): List<ThemeEntry> {
    val o = JSONObject(text)
    require(o.getInt("schema") == 1)
    val entries = o.getJSONArray("themes").also { require(it.length() <= 30) }
    return (0 until entries.length()).map { ThemeEntry.parse(entries.getJSONObject(it)) }
        .also { require(it.map { e -> e.id }.distinct().size == it.size) }
}
