package tv.own.owntv.core.epg

import java.text.Normalizer
import java.util.Locale

enum class PiconStyle { ORIGINAL, TRANSPARENT, COLOR, WIKIMEDIA;
    companion object {
        fun fromStored(value: String?): PiconStyle =
            if (value == null) WIKIMEDIA else entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}

/** Exact names first, then quality variants. Never use substring/fuzzy matches: numbered sports
 * feeds, +1 channels and regional editions must not accidentally take another channel's artwork. */
class PiconCatalog private constructor(private val exact: Map<String, Entry>, private val base: Map<String, Entry>) {
    data class Entry(val transparent: String?, val color: String?, val wikimedia: String? = null) {
        fun url(style: PiconStyle): String? = when (style) {
            PiconStyle.ORIGINAL -> null
            PiconStyle.TRANSPARENT -> transparent
            PiconStyle.COLOR -> color
            PiconStyle.WIKIMEDIA -> wikimedia
        }?.let {
            if (style == PiconStyle.WIKIMEDIA) "file:///android_asset/channel-logos/wikimedia/$it"
            else "file:///android_asset/picons/$it"
        }
    }

    fun iconFor(name: String, style: PiconStyle): String? {
        if (style == PiconStyle.ORIGINAL) return null
        val key = normalize(name)
        return exact[key]?.url(style) ?: base[withoutQuality(key)]?.url(style)
    }

    companion object {
        private val marks = Regex("\\p{M}+")
        private val punctuation = Regex("[^a-z0-9]+")
        private val quality = Regex("(?:fullhd|fhd|uhd|hd|sd|4k|1080p|720p)$")
        private val countryPrefix = Regex("^(?:\\[es]|es[|:]|espana[|:])\\s*")
        private val movistarPrefix = Regex("^m\\s*\\+\\s*")

        internal fun normalize(name: String): String {
            var text = Normalizer.normalize(name, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT).trim()
            text = text.replace(countryPrefix, "").replace(movistarPrefix, "movistar ")
                .replace("+", " plus ")
            text = text.replace(punctuation, "")
                .replace("ligadecampeones", "ligacampeones")
                .replace("lcampeones", "ligacampeones")
                .replace("lcampeon", "ligacampeones")
            return text
        }

        private fun withoutQuality(key: String): String {
            var result = key
            do { val old = result; result = result.replace(quality, "") } while (result != old)
            return when (result) {
                "clan" -> "clantve"
                "tdp" -> "teledeporte"
                "nationalgeographic", "natgeographic", "natgeograph" -> "nationalgeographic"
                "natgeowild", "ngwild", "nationalgeographicwild" -> "nationalgeographicwild"
                "movistarellasvamos", "movistarellasv" -> "movistarellasvamos"
                else -> result
            }
        }

        fun fromTsv(lines: Sequence<String>): PiconCatalog {
            val exact = linkedMapOf<String, Entry>()
            val validPath = Regex("(?:transparent|color)/[a-f0-9]{24}\\.png")
            lines.filter(String::isNotBlank).forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@forEach
                fun path(value: String) = value.takeIf { validPath.matches(it) }
                val entry = Entry(path(parts[1]), path(parts[2]))
                val key = normalize(parts[0])
                if (key.isNotEmpty()) exact.putIfAbsent(key, entry)
            }
            return indexed(exact)
        }

        /** A separate two-column index keeps Commons independent of the Jungle Team importer. */
        fun fromWikimediaTsv(lines: Sequence<String>): PiconCatalog {
            val exact = linkedMapOf<String, Entry>()
            val validPath = Regex("[a-f0-9]{24}\\.svg")
            lines.filter(String::isNotBlank).forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 2 || !validPath.matches(parts[1])) return@forEach
                val key = normalize(parts[0])
                if (key.isNotEmpty()) exact.putIfAbsent(key, Entry(null, null, parts[1]))
            }
            return indexed(exact)
        }

        private fun indexed(exact: Map<String, Entry>): PiconCatalog {
            val base = linkedMapOf<String, Entry>()
            // Prefer a channel's unqualified entry; HD/UHD remain available through exact matches.
            exact.entries.sortedBy { if (withoutQuality(it.key) == it.key) 0 else 1 }.forEach { (key, entry) ->
                base.putIfAbsent(withoutQuality(key), entry)
            }
            return PiconCatalog(exact, base)
        }
    }
}
