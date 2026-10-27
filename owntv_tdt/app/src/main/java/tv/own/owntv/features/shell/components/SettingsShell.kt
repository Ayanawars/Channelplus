package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    var selected by rememberSaveable { mutableIntStateOf(start?.group ?: SettingsGroup.APP.ordinal) }
    var navigationVersion by remember { mutableIntStateOf(0) }
    var sidebarFocused by remember { mutableStateOf(false) }
    val targets = remember { SettingsGroup.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    val active = SettingsGroup.entries[selected.coerceIn(0, SettingsGroup.entries.lastIndex)]
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
    CompositionLocalProvider(LocalSettingsNavigation provides navigation) {
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

private val categoryOrder = listOf(
    SettingsGroup.APP, SettingsGroup.APPEARANCE, SettingsGroup.LAYOUT, SettingsGroup.LIVE,
    SettingsGroup.PLAYER, SettingsGroup.PICTURE, SettingsGroup.SOUND, SettingsGroup.WATCHING,
    SettingsGroup.SOURCES, SettingsGroup.CONTENT, SettingsGroup.PROFILE, SettingsGroup.QUICK,
)

private val SettingsGroup.sidebarIcon: OwnTVIcon get() = when (this) {
    SettingsGroup.APP -> OwnTVIcon.SETTINGS
    SettingsGroup.APPEARANCE -> OwnTVIcon.PALETTE
    SettingsGroup.LAYOUT -> OwnTVIcon.GRID
    SettingsGroup.LIVE -> OwnTVIcon.LIVE_TV
    SettingsGroup.PLAYER -> OwnTVIcon.PLAY_CIRCLE
    SettingsGroup.PICTURE -> OwnTVIcon.EXPAND
    SettingsGroup.SOUND -> OwnTVIcon.HEADPHONES
    SettingsGroup.WATCHING -> OwnTVIcon.REC
    SettingsGroup.SOURCES -> OwnTVIcon.LIST
    SettingsGroup.CONTENT -> OwnTVIcon.IMAGE
    SettingsGroup.PROFILE -> OwnTVIcon.PERSON
    SettingsGroup.QUICK -> OwnTVIcon.SPARKLE
}

@Composable
internal fun settingsCategoryTitle(group: SettingsGroup): String = stringResource(when (group) {
    SettingsGroup.APP -> R.string.settings_menu_general
    SettingsGroup.LAYOUT -> R.string.settings_menu_navigation
    SettingsGroup.SOURCES -> R.string.settings_menu_sources_guide
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
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.channel_brand_name), style = stageText(30, 800), color = StageColors.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.common_nav_settings), style = stageText(17, 500), color = StageColors.Muted,
            modifier = Modifier.padding(top = 6.mpx, bottom = 30.mpx), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(
            Modifier.weight(1f).focusGroup().onFocusChanged { onFocus(it.hasFocus) }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.mpx),
        ) {
            categoryOrder.forEach { group ->
                val selected = active == group
                StageSurface(
                    onClick = { onChoose(group) }, radius = 10.mpx, focusStyle = StageFocus.FX,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.mpx)
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
                        Box(Modifier.width(4.mpx).height(28.mpx)
                            .then(if (selected) Modifier.background(StageColors.Text) else Modifier))
                        OwnTVIcon(group.sidebarIcon, if (selected || focused) StageColors.Text else StageColors.Muted, Modifier.size(25.mpx))
                        Text(settingsCategoryTitle(group), style = stageText(21, if (selected) 700 else 500),
                            color = if (selected || focused) StageColors.Text else StageColors.Muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.mpx))
                    }
                }
            }
        }
    }
}
