package tv.own.owntv.features.shell.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import org.json.JSONObject
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageAccent
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** The display order is data; actions still execute in the existing native settings screens. */
internal data class OrderedSettingsTarget(val category: SettingsGroup, val detail: String? = null)
private data class OrderedOption(val id: String, val title: String, val type: String)
private data class OrderedGroup(val id: String, val title: String, val options: List<OrderedOption>)
private data class OrderedSection(val id: String, val title: String, val groups: List<OrderedGroup>)

private fun loadOrderedSettings(context: android.content.Context): List<OrderedSection> = runCatching {
    val json = context.assets.open("stayon_settings_order.json").bufferedReader().use { it.readText() }
    val array = JSONObject(json).getJSONObject("config").getJSONArray("menu")
    List(array.length()) { index ->
        val section = array.getJSONObject(index)
        val groups = section.getJSONArray("groups")
        OrderedSection(section.getString("id"), section.getString("title"), List(groups.length()) { gi ->
            val group = groups.getJSONObject(gi)
            val options = group.getJSONArray("options")
            OrderedGroup(group.getString("id"), group.getString("title"), List(options.length()) { oi ->
                val option = options.getJSONObject(oi)
                OrderedOption(option.getString("id"), option.getString("label"), option.optString("type", "action"))
            })
        })
    }
}.getOrDefault(emptyList())

/** Stable destination mapping: never interpret labels as executable commands. */
private fun orderedTarget(section: String, group: String, option: String): OrderedSettingsTarget = when (section) {
    "sources_and_content" -> when (group) {
        "tv_radio_channels" -> when (option) {
            "edit_channels" -> OrderedSettingsTarget(SettingsGroup.SOURCES, "CUSTOMIZE")
            else -> OrderedSettingsTarget(SettingsGroup.SOURCES, "SOURCES")
        }
        "epg_guide" -> OrderedSettingsTarget(SettingsGroup.SOURCES, if (option == "time_offset") null else "EPG")
        "catchup" -> OrderedSettingsTarget(SettingsGroup.SOURCES)
        "metadata" -> OrderedSettingsTarget(SettingsGroup.SOURCES, "METADATA")
        else -> OrderedSettingsTarget(SettingsGroup.SOURCES)
    }
    "playback_and_live" -> when (group) {
        "playback_engines" -> OrderedSettingsTarget(SettingsGroup.PLAYER)
        "video_image" -> OrderedSettingsTarget(SettingsGroup.PICTURE)
        "audio_sound" -> OrderedSettingsTarget(SettingsGroup.SOUND)
        "subtitles" -> if (option == "opensubtitles_integration")
            OrderedSettingsTarget(SettingsGroup.SOUND, "OPEN_SUBTITLES")
            else OrderedSettingsTarget(SettingsGroup.SOUND)
        "live_control" -> if (option == "recordings")
            OrderedSettingsTarget(SettingsGroup.WATCHING)
            else OrderedSettingsTarget(SettingsGroup.LIVE)
        "continuity" -> OrderedSettingsTarget(SettingsGroup.WATCHING)
        else -> OrderedSettingsTarget(SettingsGroup.PLAYER)
    }
    "home_sections" -> if (group == "sports_mode") OrderedSettingsTarget(SettingsGroup.HOME, "SPORTS")
        else OrderedSettingsTarget(SettingsGroup.HOME, "HOME")
    "ui_appearance" -> when (group) {
        "panels_guide" -> OrderedSettingsTarget(SettingsGroup.APPEARANCE,
            if (option == "guide_layout") "GUIDE_WIDTH" else "PANEL_WIDTH")
        "clock_weather" -> OrderedSettingsTarget(SettingsGroup.APPEARANCE, "WEATHER")
        "visual_style" -> when (option) {
            "fonts_scale" -> OrderedSettingsTarget(SettingsGroup.APPEARANCE, "FONTS")
            else -> OrderedSettingsTarget(SettingsGroup.APPEARANCE)
        }
        else -> OrderedSettingsTarget(SettingsGroup.APPEARANCE)
    }
    "remote_navigation" -> when (group) {
        "channel_nav" -> OrderedSettingsTarget(SettingsGroup.REMOTE, "CH_NAV")
        "context_menus" -> OrderedSettingsTarget(SettingsGroup.REMOTE, "CONTENT_MENUS")
        else -> OrderedSettingsTarget(SettingsGroup.REMOTE)
    }
    "apps_services" -> when (group) {
        "music_integration" -> OrderedSettingsTarget(SettingsGroup.MUSIC)
        else -> if (option == "launcher_mode") OrderedSettingsTarget(SettingsGroup.SYSTEM)
            else OrderedSettingsTarget(SettingsGroup.APPLICATIONS)
    }
    "system_network" -> when (group) {
        "backups" -> OrderedSettingsTarget(SettingsGroup.SYSTEM, "BACKUP")
        "sync" -> OrderedSettingsTarget(SettingsGroup.SYSTEM, "LOCAL_SYNC")
        "network_dns" -> OrderedSettingsTarget(SettingsGroup.SYSTEM, if (option == "custom_dns") "DNS" else "NETWORK")
        else -> OrderedSettingsTarget(SettingsGroup.SYSTEM)
    }
    else -> OrderedSettingsTarget(SettingsGroup.APP)
}

@Composable
internal fun StayonOrderedSettingsHub(
    sectionIndex: Int,
    groupIndex: Int,
    onSectionChange: (Int) -> Unit,
    onGroupChange: (Int) -> Unit,
    onOpenSetting: (OrderedSettingsTarget) -> Unit,
    onOpenGeneral: () -> Unit,
) {
    val context = LocalContext.current
    val sections = remember(context) { loadOrderedSettings(context) }
    val activeSection = sections.getOrNull(sectionIndex)
    val activeGroup = activeSection?.groups?.getOrNull(groupIndex)
    val lemon = Color(0xFFF4F29A)
    val ivory = Color(0xFFF5F3E8)
    val focus = remember(sectionIndex, groupIndex) { FocusRequester() }

    BackHandler(enabled = sectionIndex >= 0) {
        if (groupIndex >= 0) onGroupChange(-1) else onSectionChange(-1)
    }
    LaunchedEffect(sectionIndex, groupIndex) {
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 52.mpx, vertical = 26.mpx)) {
        Text("STAYON+  /  CONFIGURACIÓN", style = stageText(30, 800), color = ivory)
        Text(
            when {
                activeGroup != null -> activeSection!!.title + "   ›   " + activeGroup.title
                activeSection != null -> activeSection.title
                else -> "CONFIGURACIÓN · SECCIONES"
            },
            style = stageText(18, 500), color = lemon,
            modifier = Modifier.padding(top = 9.mpx, bottom = 14.mpx),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().padding(bottom = 12.mpx).background(lemon).heightIn(min = 2.mpx))
        if (sections.isEmpty()) {
            Text("No se pudo cargar el menú. Abre Ajustes generales para acceder a las opciones originales.",
                style = stageText(19, 500), color = ivory)
            StageSurface(onClick = onOpenGeneral, radius = 10.mpx,
                focusStyle = StageFocus.FX, modifier = Modifier.fillMaxWidth().focusRequester(focus)) {
                Text("Abrir ajustes generales", style = stageText(22, 600), color = ivory, modifier = Modifier.padding(18.mpx))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.mpx)) {
                if (activeGroup != null) {
                    itemsIndexed(activeGroup.options) { i, option ->
                        OrderedMenuRow(
                            title = option.title,
                            subtitle = "Abrir ajuste nativo",
                            index = i,
                            initialFocus = focus,
                            icon = OwnTVIcon.SETTINGS,
                        ) {
                            if (option.id == "native_settings") {
                                val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            } else {
                                onOpenSetting(orderedTarget(activeSection!!.id, activeGroup.id, option.id))
                            }
                        }
                    }
                } else if (activeSection != null) {
                    itemsIndexed(activeSection.groups) { i, group ->
                        OrderedMenuRow(
                            title = group.title,
                            subtitle = group.options.size.toString() + " opciones",
                            index = i,
                            initialFocus = focus,
                            icon = OwnTVIcon.LIST,
                        ) { onGroupChange(i) }
                    }
                } else {
                    itemsIndexed(sections) { i, section ->
                        OrderedMenuRow(
                            title = section.title,
                            subtitle = section.groups.size.toString() + " grupos",
                            index = i,
                            initialFocus = focus,
                            icon = sectionIcon(i),
                        ) { onSectionChange(i) }
                    }
                    item {
                        OrderedMenuRow(
                            title = "Ajustes generales y rápidos",
                            subtitle = "Idioma, inicio, preferencias y acceso a los ajustes clásicos",
                            index = -1,
                            initialFocus = focus,
                            icon = OwnTVIcon.SETTINGS,
                            onClick = onOpenGeneral,
                        )
                    }
                    item {
                        OrderedMenuRow(
                            title = "Ajustes del televisor",
                            subtitle = "Abrir los ajustes de Android TV",
                            index = -1,
                            initialFocus = focus,
                            icon = OwnTVIcon.SETTINGS,
                        ) {
                            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }
            }
        }
    }
}

private fun sectionIcon(index: Int): OwnTVIcon = when (index) {
    0 -> OwnTVIcon.LIST
    1 -> OwnTVIcon.PLAY_CIRCLE
    2 -> OwnTVIcon.HOME
    3 -> OwnTVIcon.PALETTE
    4 -> OwnTVIcon.MENU
    5 -> OwnTVIcon.GRID
    else -> OwnTVIcon.SETTINGS
}

@Composable
private fun OrderedMenuRow(
    title: String,
    subtitle: String,
    index: Int,
    initialFocus: FocusRequester,
    icon: OwnTVIcon,
    onClick: () -> Unit,
) {
    val lemon = Color(0xFFF4F29A)
    StageSurface(
        onClick = onClick,
        radius = 12.mpx,
        focusStyle = StageFocus.FX,
        accent = StageAccent(lemon, Color.Black, lemon),
        idle = Modifier.background(Color(0xFF141414), RoundedCornerShape(12.mpx)),
        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.mpx).padding(horizontal = 18.mpx, vertical = 12.mpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
            OwnTVIcon(icon, lemon, Modifier.size(25.mpx))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                Text(title, style = stageText(22, 650), color = Color(0xFFF5F3E8),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = stageText(15, 450), color = Color(0xFFAAA99E),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.mpx))
            Text("›", style = stageText(28, 500), color = lemon)
        }
    }
}
