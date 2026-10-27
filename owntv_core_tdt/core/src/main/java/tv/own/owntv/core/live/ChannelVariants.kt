package tv.own.owntv.core.live

import java.text.Normalizer
import java.util.Locale
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.TifChannelUri

/** Non-destructive station grouping. Regional names and country prefixes remain part of the key.
 * Guide ids are deliberately not identity: providers often reuse one id for regional feeds. */
object ChannelVariants {
    private val quality = Regex("""(?i)(?<![\p{L}\p{N}])(4K|UHD|FHD|FULL[ -]?HD|1080[PI]?|720[PI]?|HD|SD|2160[PI]?)(?![\p{L}\p{N}])""")
    private val source = Regex("""(?i)(?<![\p{L}\p{N}])(TDT|IPTV)(?![\p{L}\p{N}])""")
    private val separators = Regex("""[\s\[\](){}|_-]+""")

    fun isTdt(channel: ChannelEntity): Boolean = TifChannelUri.parse(channel.streamUrl) != null
    private fun plain(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
    fun displayName(channel: ChannelEntity): String {
        val raw = plain(channel.name)
        val parsed = ProviderTags.parse(raw)
        val base = separators.replace(source.replace(quality.replace(parsed.name, " "), " "), " ").trim()
        val display = base.ifBlank { channel.name }
        return if (parsed.country == null || parsed.country in setOf("ES", "ESP")) display
            else "${parsed.country}| $display"
    }
    fun key(channel: ChannelEntity): String {
        val parsed = ProviderTags.parse(plain(channel.name))
        // Unprefixed Spanish tuner names match ES IPTV; other countries never cross that boundary.
        val country = when (parsed.country) { null, "ESP" -> "ES"; else -> parsed.country }
        val name = Normalizer.normalize(ProviderTags.parse(displayName(channel)).name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(" ", "")
        return "$country:$name"
    }
    fun qualityLabel(channel: ChannelEntity): String? {
        TifChannelUri.parse(channel.streamUrl)?.videoHeight?.let { height ->
            return when { height >= 2160 -> "UHD"; height >= 1080 -> "FHD"; height >= 720 -> "HD"; else -> "SD" }
        }
        val tokens = quality.findAll(plain(channel.name)).map { it.value.uppercase(Locale.ROOT).replace(" ", "").replace("-", "") }.toList()
        return when {
            tokens.any { it in listOf("4K", "UHD", "2160", "2160P", "2160I") } -> "UHD"
            tokens.any { it in listOf("FHD", "FULLHD", "1080", "1080P", "1080I") } -> "FHD"
            tokens.any { it in listOf("HD", "720", "720P", "720I") } -> "HD"
            "SD" in tokens -> "SD"
            else -> null // Never invent FHD for a tuner whose format is unknown.
        }
    }
    private fun qualityRank(channel: ChannelEntity): Int = when (qualityLabel(channel)) {
        "UHD" -> 4; "FHD" -> 3; "HD" -> 2; "SD" -> 1; else -> 0
    }
    val preference: Comparator<ChannelEntity> = compareBy<ChannelEntity> { if (isTdt(it)) 0 else 1 }
        .thenByDescending { qualityRank(it) }.thenBy { it.sourceId }.thenBy { it.sortOrder }.thenBy { it.id }

    /** Preserve the first station's position in each rail, but pick its preferred member. */
    fun collapse(channels: List<ChannelEntity>): List<ChannelEntity> = channels.groupBy(::key).values.map { variants ->
        variants.minWith(preference).let { it.copy(name = displayName(it)) }
    }
    fun variants(channels: List<ChannelEntity>, channel: ChannelEntity): List<ChannelEntity> =
        channels.filter { key(it) == key(channel) }.sortedWith(preference)
}
