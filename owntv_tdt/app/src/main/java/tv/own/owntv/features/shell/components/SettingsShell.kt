package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.features.settings.LocalSettingsNavigation
import tv.own.owntv.features.settings.SettingsNavigation
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StagePalette
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageAccent
import tv.own.owntv.ui.theme.LocalSettingsAccentOverride
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** One persistent category menu, including while a nested settings page is open. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    fontCustomization: FontCustomization,
    onSetFontCustomization: (FontCustomization) -> Unit,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
    start: SettingsStart? = null,
    onStartConsumed: () -> Unit = {},
) {
    var selected by rememberSaveable { mutableIntStateOf(start?.group?.takeUnless { it == SettingsGroup.PROFILE.ordinal } ?: SettingsGroup.APP.ordinal) }
    var navigationVersion by remember { mutableIntStateOf(0) }
    var sidebarFocused by remember { mutableStateOf(false) }
    val targets = remember { SettingsGroup.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    val active = sidebarParent(SettingsGroup.entries[selected.coerceIn(0, SettingsGroup.entries.lastIndex)])
    val navigation = SettingsNavigation(
        categoryFocus = targets.getValue(active),
        contentFocus = contentFocus,
        sidebar = {
            SettingsSidebar(active, targets, contentFocus,
                onFocus = { sidebarFocused = it },
                onChoose = {
                    selected = it.ordinal
                    navigationVersion++
                },
            )
        },
    )
    CompositionLocalProvider(
        LocalSettingsNavigation provides navigation,
        LocalSettingsAccentOverride provides StageAccent(Color(0xFFF4F29A), Color.Black, Color(0xFFF4F29A)),
        LocalStagePalette provides StagePalette.Dark.copy(
            page = Color.Black, wash = Color.Black,
            text = Color(0xFFF5F3E8), muted = Color(0xFFBDBCB2),
            dim = Color(0xFF88887E), body = Color(0xFFD6D4C9),
            ink = Color.White, glassTint = Color(0xFF141414), glassOff = Color(0xFF141414),
        ),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Black)) {
        SettingsContent(
            themeMode, uiZoomPercent, onSetZoom, fontCustomization, onSetFontCustomization,
            onOpenPlaylist, modifier,
            onBack = {
                if (sidebarFocused) onBack?.invoke()
                else runCatching { targets.getValue(active).requestFocus() }
            },
            openEpgAdd = openEpgAdd, onEpgAddConsumed = onEpgAddConsumed,
            start = start, onStartConsumed = onStartConsumed,
            selectedGroup = selected, onSelectGroup = { selected = it }, navigationVersion = navigationVersion,
        )
      }
    }
}

/** Nine top-level sections; legacy detail pages remain reachable from their parent section. */
private val categoryOrder = listOf(
    SettingsGroup.APP, SettingsGroup.SOURCES, SettingsGroup.HOME,
    SettingsGroup.PLAYER, SettingsGroup.APPEARANCE, SettingsGroup.REMOTE,
    SettingsGroup.MUSIC, SettingsGroup.APPLICATIONS, SettingsGroup.SYSTEM,
)

private fun sidebarParent(group: SettingsGroup): SettingsGroup = when (group) {
    SettingsGroup.QUICK, SettingsGroup.PROFILE -> SettingsGroup.APP
    SettingsGroup.CONTENT -> SettingsGroup.SOURCES
    SettingsGroup.PICTURE, SettingsGroup.SOUND, SettingsGroup.LIVE, SettingsGroup.WATCHING -> SettingsGroup.PLAYER
    SettingsGroup.LAYOUT -> SettingsGroup.APPEARANCE
    else -> group
}

private val SettingsGroup.sidebarIcon: OwnTVIcon get() = when (this) {
    SettingsGroup.APP, SettingsGroup.SYSTEM -> OwnTVIcon.SETTINGS
    SettingsGroup.SOURCES, SettingsGroup.CONTENT -> OwnTVIcon.LIST
    SettingsGroup.HOME -> OwnTVIcon.HOME
    SettingsGroup.PLAYER, SettingsGroup.LIVE -> OwnTVIcon.PLAY_CIRCLE
    SettingsGroup.APPEARANCE, SettingsGroup.PICTURE, SettingsGroup.LAYOUT -> OwnTVIcon.PALETTE
    SettingsGroup.REMOTE -> OwnTVIcon.MENU
    SettingsGroup.MUSIC, SettingsGroup.SOUND -> OwnTVIcon.AUDIO
    SettingsGroup.APPLICATIONS -> OwnTVIcon.GRID
    SettingsGroup.WATCHING -> OwnTVIcon.REC
    SettingsGroup.PROFILE -> OwnTVIcon.PERSON
    SettingsGroup.QUICK -> OwnTVIcon.SPARKLE
}

@Composable
internal fun settingsCategoryTitle(group: SettingsGroup): String = stringResource(when (group) {
    SettingsGroup.APP -> R.string.stayon_settings_general
    SettingsGroup.SOURCES -> R.string.stayon_settings_sources
    SettingsGroup.PLAYER -> R.string.stayon_settings_playback
    SettingsGroup.APPEARANCE -> R.string.stayon_settings_appearance
    SettingsGroup.HOME -> R.string.stayon_settings_home
    SettingsGroup.REMOTE -> R.string.stayon_settings_remote
    SettingsGroup.MUSIC -> R.string.stayon_settings_music
    SettingsGroup.APPLICATIONS -> R.string.stayon_settings_apps
    SettingsGroup.SYSTEM -> R.string.stayon_settings_system
    SettingsGroup.LAYOUT -> R.string.settings_menu_navigation
    SettingsGroup.CONTENT -> R.string.settings_menu_artwork
    else -> group.titleRes
})

@Composable
private fun SettingsSidebar(
    active: SettingsGroup,
    targets: Map<SettingsGroup, FocusRequester>,
    contentFocus: FocusRequester,
    onFocus: (Boolean) -> Unit,
    onChoose: (SettingsGroup) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lemon = Color(0xFFF4F29A)
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.channel_brand_name), style = stageText(30, 800), color = StageColors.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.common_nav_settings), style = stageText(17, 500), color = StageColors.Muted,
            modifier = Modifier.padding(top = 6.mpx, bottom = 30.mpx), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StageSurface(
            onClick = {
                val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            },
            radius = 12.mpx, focusStyle = StageFocus.FILLED,
            accent = StageAccent(lemon, Color.Black, lemon),
            idle = Modifier.background(Color(0xFF151515), RoundedCornerShape(12.mpx)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.mpx).padding(bottom = 8.mpx),
        ) { focused ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 12.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.SETTINGS, if (focused) Color.Black else lemon, Modifier.size(25.mpx))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stayon_tv_settings), style = stageText(21, 700), color = if (focused) Color.Black else StageColors.Text)
                    Text(stringResource(R.string.stayon_tv_settings_desc), style = stageText(14, 500), color = if (focused) Color.Black.copy(alpha = 0.74f) else StageColors.Muted)
                }
                OwnTVIcon(OwnTVIcon.CHEVRON, if (focused) Color.Black else StageColors.Muted, Modifier.size(18.mpx))
            }
        }
        Column(
            Modifier.weight(1f).focusGroup().onFocusChanged { onFocus(it.hasFocus) }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.mpx),
        ) {
            categoryOrder.forEach { group ->
                val selected = active == group
                StageSurface(
                    onClick = { onChoose(group) }, radius = 12.mpx, focusStyle = StageFocus.FILLED,
                    accent = StageAccent(lemon, Color.Black, lemon),
                    idle = if (selected) Modifier.background(lemon, RoundedCornerShape(12.mpx)) else Modifier,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 62.mpx)
                        .focusRequester(targets.getValue(group))
                        .focusProperties { right = contentFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionRight) {
                                if (event.type == KeyEventType.KeyDown) {
                                    onChoose(group)
                                    scope.launch {
                                        repeat(4) { withFrameNanos { } }
                                        runCatching { contentFocus.requestFocus() }
                                    }
                                }
                                true
                            } else false
                        },
                ) { focused ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.mpx), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                        OwnTVIcon(group.sidebarIcon, if (selected || focused) Color.Black else StageColors.Muted, Modifier.size(25.mpx))
                        Text(settingsCategoryTitle(group), style = stageText(21, if (selected) 700 else 500),
                            color = if (selected || focused) Color.Black else StageColors.Muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.mpx))
                    }
                }
            }
        }
    }
}
