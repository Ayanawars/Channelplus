package tv.own.owntv.core.settings

enum class StayonHomeWidget { LAST_CHANNEL, GUIDE, FAVORITE, SPORTS, BROADCASTERS }
enum class StayonHomeTab { TV, RADIO, MUSIC, APPS }

data class StayonHomeLayout(
    val scale: Int = 100,
    val startup: StayonHomeTab = StayonHomeTab.TV,
    val order: List<StayonHomeWidget> = StayonHomeWidget.entries,
    val hidden: Set<StayonHomeWidget> = emptySet(),
    val large: Set<StayonHomeWidget> = emptySet(),
    val tabs: List<StayonHomeTab> = StayonHomeTab.entries,
) {
    internal fun encode(): String = listOf(scale, startup.name, order.joinToString(","), hidden.joinToString(","),
        large.joinToString(","), tabs.joinToString(",")).joinToString("|")

    companion object {
        internal fun decode(raw: String?): StayonHomeLayout {
            val fields = raw?.split('|').orEmpty()
            if (fields.size != 6) return StayonHomeLayout()
            fun widgets(index: Int) = fields[index].split(',').mapNotNull { key ->
                StayonHomeWidget.entries.firstOrNull { it.name == key }
            }.distinct()
            val tabs = fields[5].split(',').mapNotNull { key ->
                StayonHomeTab.entries.firstOrNull { it.name == key }
            }.distinct().ifEmpty { StayonHomeTab.entries }
            return StayonHomeLayout(
                scale = fields[0].toIntOrNull()?.coerceIn(70, 130) ?: 100,
                startup = tabs.firstOrNull { it.name == fields[1] } ?: tabs.first(),
                order = (widgets(2) + StayonHomeWidget.entries).distinct(),
                hidden = widgets(3).toSet(),
                large = widgets(4).toSet(),
                tabs = tabs,
            )
        }
    }
}
