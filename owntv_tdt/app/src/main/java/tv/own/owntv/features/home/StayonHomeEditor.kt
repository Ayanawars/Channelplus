package tv.own.owntv.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.settings.StayonHomeLayout
import tv.own.owntv.core.settings.StayonHomeTab
import tv.own.owntv.core.settings.StayonHomeWidget
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsHeading
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup

@Composable
internal fun homeTabLabel(tab: StayonHomeTab): String = stringResource(when (tab) {
    StayonHomeTab.TV -> R.string.stayon_tv
    StayonHomeTab.RADIO -> R.string.stayon_nav_radio
    StayonHomeTab.MUSIC -> R.string.stayon_music
    StayonHomeTab.APPS -> R.string.stayon_nav_apps
})

@Composable
internal fun StayonHomeEditor(
    initial: StayonHomeLayout,
    onPreview: (StayonHomeLayout) -> Unit,
    onSave: (StayonHomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val first = remember { FocusRequester() }
    fun update(layout: StayonHomeLayout) { draft = layout; onPreview(layout) }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.stayon_home_edit), buttons = {
        StageButton(stringResource(R.string.common_cancel), onClick = onDismiss)
        StageButton(stringResource(R.string.common_save), onClick = { onSave(draft) }, tinted = true)
    }) {
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        StageSettingRow(OwnTVIcon.ZOOM, stringResource(R.string.stayon_home_scale), null,
            SettingValue.Stepper(stringResource(R.string.common_percent, draft.scale)),
            modifier = Modifier.focusRequester(first),
            onStep = { update(draft.copy(scale = (draft.scale + 5 * it).coerceIn(70, 130))) },
            onClick = { update(draft.copy(scale = if (draft.scale >= 130) 70 else draft.scale + 5)) })
        StageSettingRow(OwnTVIcon.HOME, stringResource(R.string.stayon_home_start_tab), null,
            SettingValue.Choice(homeTabLabel(draft.startup)),
            onClick = { update(draft.copy(startup = draft.tabs[(draft.tabs.indexOf(draft.startup) + 1) % draft.tabs.size])) })
        StageSettingsHeading(stringResource(R.string.stayon_home_tabs), null)
        StayonHomeTab.entries.forEach { tab ->
            val visible = tab in draft.tabs
            StageSettingRow(OwnTVIcon.MENU, homeTabLabel(tab), null, SettingValue.Switch(visible),
                enabled = !visible || draft.tabs.size > 1,
                onClick = {
                    val tabs = if (visible) draft.tabs - tab else draft.tabs + tab
                    update(draft.copy(tabs = tabs, startup = draft.startup.takeIf { it in tabs } ?: tabs.first()))
                })
        }
        StageSettingsHeading(stringResource(R.string.stayon_home_widgets), null)
        draft.order.forEach { widget ->
            val visible = widget !in draft.hidden
            val label = stringResource(when (widget) {
                StayonHomeWidget.LAST_CHANNEL -> R.string.stayon_last_channel
                StayonHomeWidget.GUIDE -> R.string.stayon_guide
                StayonHomeWidget.FAVORITE -> R.string.stayon_home_favorite
                StayonHomeWidget.SPORTS -> R.string.stayon_home_sports
                StayonHomeWidget.BROADCASTERS -> R.string.stayon_home_broadcasters
            })
            StageSettingRow(OwnTVIcon.GRID, label, stringResource(R.string.stayon_home_widget_controls),
                SettingValue.Switch(visible),
                onClick = { update(draft.copy(hidden = if (visible) draft.hidden + widget else draft.hidden - widget)) },
                onStep = { delta ->
                    val order = draft.order.toMutableList()
                    val index = order.indexOf(widget)
                    if (index + delta in order.indices) {
                        order.add(index + delta, order.removeAt(index))
                        update(draft.copy(order = order))
                    }
                },
                onLongClick = { update(draft.copy(large = if (widget in draft.large) draft.large - widget else draft.large + widget)) })
        }
        StageSettingRow(OwnTVIcon.REFRESH, stringResource(R.string.common_reset), null, null,
            onClick = { update(StayonHomeLayout()) })
    }
}
