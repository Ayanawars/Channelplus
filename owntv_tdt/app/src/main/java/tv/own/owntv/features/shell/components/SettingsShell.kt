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
    // A full-width category hub instead of the old permanent left navigation rail.
    // Deep links may still open a category directly.
    val initialGroup = start?.group?.takeUnless { it == SettingsGroup.PROFILE.ordinal }
        ?: if (openEpgAdd) SettingsGroup.SOURCES.ordinal else -1
    var selected by rememberSaveable { mutableIntStateOf(initialGroup) }
    var navigationVersion by remember { mutableIntStateOf(0) }
    val contentFocus = remember { FocusRequester() }
    val navigation = SettingsNavigation(
        categoryFocus = contentFocus,
        contentFocus = contentFocus,
        sidebar = {},
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
        Box(modifier.fillMaxSize().background(Color.Black)) {
            if (selected < 0) {
                androidx.activity.compose.BackHandler(enabled = onBack != null) { onBack?.invoke() }
                SettingsHub(
                    onChoose = { group ->
                        selected = group.ordinal
                        navigationVersion++
                    },
                )
            } else {
                SettingsContent(
                    themeMode, uiZoomPercent, onSetZoom, fontCustomization, onSetFontCustomization,
                    onOpenPlaylist, Modifier.fillMaxSize(),
                    onBack = { selected = -1 },
                    openEpgAdd = openEpgAdd, onEpgAddConsumed = onEpgAddConsumed,
                    start = start, onStartConsumed = onStartConsumed,
                    selectedGroup = selected,
                    onSelectGroup = { selected = it },
                    navigationVersion = navigationVersion,
                )
            }
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

/** The first (and only) category selector: no persistent left rail on detail pages. */
@Composable
private fun SettingsHub(onChoose: (SettingsGroup) -> Unit) {
    val context = LocalContext.current
    val lemon = Color(0xFFF4F29A)
    val ivory = Color(0xFFF5F3E8)
    val nativeFocus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repeat(3) { withFrameNanos { } }
        runCatching { nativeFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().padding(
            start = 58.mpx, end = 58.mpx, top = 128.mpx, bottom = 38.mpx,
        ),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.channel_brand_name), style = stageText(43, 800), color = ivory)
            Spacer(Modifier.width(30.mpx))
            Box(Modifier.width(2.mpx).height(43.mpx).background(Color(0xFF565650)))
            Spacer(Modifier.width(30.mpx))
            Text(stringResource(R.string.common_nav_settings), style = stageText(35, 700), color = ivory,
                modifier = Modifier.weight(1f))
            StageSurface(
                onClick = { onChoose(SettingsGroup.QUICK) },
                radius = 10.mpx, focusStyle = StageFocus.FILLED,
                accent = StageAccent(lemon, Color.Black, lemon),
                idle = Modifier.background(Color(0xFF171717), RoundedCornerShape(10.mpx)),
                modifier = Modifier.height(48.mpx),
            ) { focused ->
                Row(Modifier.padding(horizontal = 20.mpx), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                    OwnTVIcon(OwnTVIcon.SPARKLE, if (focused) Color.Black else lemon, Modifier.size(21.mpx))
                    Text(stringResource(R.string.settings_group_quick), style = stageText(18, 600),
                        color = if (focused) Color.Black else ivory)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.mpx).background(lemon))
        val groups: List<SettingsGroup?> = listOf(null) + categoryOrder.map { it }
        groups.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(17.mpx),
            ) {
                rowItems.forEachIndexed { colIndex, group ->
                    val first = rowIndex == 0 && colIndex == 0
                    StageSurface(
                        onClick = {
                            if (group == null) {
                                val nativeIntent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(nativeIntent) }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                            } else onChoose(group)
                        },
                        radius = 14.mpx, focusStyle = StageFocus.FILLED,
                        accent = StageAccent(lemon, Color.Black, lemon),
                        idle = Modifier.background(Color(0xFF141414), RoundedCornerShape(14.mpx)),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .then(if (first) Modifier.focusRequester(nativeFocus) else Modifier),
                    ) { focused ->
                        Column(Modifier.fillMaxSize().padding(24.mpx),
                            verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                            OwnTVIcon(group?.sidebarIcon ?: OwnTVIcon.SETTINGS,
                                if (focused) Color.Black else lemon, Modifier.size(29.mpx))
                            Text(
                                if (group == null) stringResource(R.string.stayon_tv_settings) else settingsCategoryTitle(group),
                                style = stageText(25, 700),
                                color = if (focused) Color.Black else ivory,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (group == null) stringResource(R.string.stayon_tv_settings_desc)
                                else stringResource(settingsCategorySubtitleRes(group)),
                                style = stageText(17, 500),
                                color = if (focused) Color.Black.copy(alpha = 0.77f) else StageColors.Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun settingsCategorySubtitleRes(group: SettingsGroup): Int = when (group) {
    SettingsGroup.APP -> R.string.stayon_desc_general
    SettingsGroup.SOURCES -> R.string.stayon_desc_sources
    SettingsGroup.HOME -> R.string.stayon_desc_home
    SettingsGroup.PLAYER -> R.string.stayon_desc_playback
    SettingsGroup.APPEARANCE -> R.string.stayon_desc_appearance
    SettingsGroup.REMOTE -> R.string.stayon_desc_remote
    SettingsGroup.MUSIC -> R.string.stayon_desc_music
    SettingsGroup.APPLICATIONS -> R.string.stayon_desc_apps
    SettingsGroup.SYSTEM -> R.string.stayon_desc_system
    else -> R.string.stayon_desc_general
}
