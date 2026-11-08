package tv.own.owntv.core.settings

enum class StayonHomeWidget { LAST_CHANNEL, GUIDE, FAVORITE, SPORTS, BROADCASTERS }
enum class StayonHomeTab { TV, RADIO, MUSIC, APPS }

enum class StayonBroadcaster { MEDIASET, RTVE, ATRESMEDIA }

data class StayonHomeLayout(
    val scale: Int = 100,
    val startup: StayonHomeTab = StayonHomeTab.TV,
    val order: List<StayonHomeWidget> = StayonHomeWidget.entries,
    val hidden: Set<StayonHomeWidget> = emptySet(),
    val large: Set<StayonHomeWidget> = emptySet(),
    val tabs: List<StayonHomeTab> = StayonHomeTab.entries,
    val logoSizes: Map<StayonBroadcaster, Int> = emptyMap(),
) {
    fun logoSize(broadcaster: StayonBroadcaster): Int =
        logoSizes[broadcaster]?.coerceIn(MIN_LOGO_SIZE, MAX_LOGO_SIZE) ?: 100

    internal fun encode(): String = listOf(scale, startup.name, order.joinToString(","), hidden.joinToString(","),
        large.joinToString(","), tabs.joinToString(","),
        StayonBroadcaster.entries.joinToString(",") { logoSize(it).toString() }).joinToString("|")

    companion object {
        const val MIN_LOGO_SIZE = 60
        const val MAX_LOGO_SIZE = 130

        internal fun decode(raw: String?): StayonHomeLayout {
            val fields = raw?.split('|').orEmpty()
            if (fields.size !in 6..7) return StayonHomeLayout()
            fun widgets(index: Int) = fields[index].split(',').mapNotNull { key ->
                StayonHomeWidget.entries.firstOrNull { it.name == key }
            }.distinct()
            val tabs = fields[5].split(',').mapNotNull { key ->
                StayonHomeTab.entries.firstOrNull { it.name == key }
            }.distinct().ifEmpty { StayonHomeTab.entries }
            val logoValues = fields.getOrNull(6)?.split(',').orEmpty()
            val logoSizes = StayonBroadcaster.entries.mapIndexedNotNull { index, broadcaster ->
                logoValues.getOrNull(index)?.toIntOrNull()?.coerceIn(MIN_LOGO_SIZE, MAX_LOGO_SIZE)
                    ?.takeUnless { it == 100 }?.let { broadcaster to it }
            }.toMap()
            return StayonHomeLayout(
                scale = fields[0].toIntOrNull()?.coerceIn(70, 130) ?: 100,
                startup = tabs.firstOrNull { it.name == fields[1] } ?: tabs.first(),
                order = (widgets(2) + StayonHomeWidget.entries).distinct(),
                hidden = widgets(3).toSet(),
                large = widgets(4).toSet(),
                tabs = tabs,
                logoSizes = logoSizes,
            )
        }
    }
}
