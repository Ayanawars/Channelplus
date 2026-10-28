package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.hudInk
import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Reflows at full screen width; only chrome density changes, never the picture or VOD. */
@Composable
internal fun LiveHudSize(content: @Composable () -> Unit) {
    val settings = org.koin.compose.koinInject<SettingsRepository>()
    val percent by settings.liveHudSizePercent.collectAsStateWithLifecycle(initialValue = 70)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * percent / 100f, density.fontScale), content = content)
}

@Composable
internal fun LivePlayerFooter(
    player: PlaybackEngine, info: (@Composable () -> Unit)?, match: (@Composable () -> Unit)?,
    toolsExpanded: Boolean, onToolsExpandedChange: (Boolean) -> Unit,
    primaryFocus: FocusRequester?, seekFocus: FocusRequester?,
    sourceLabel: String?, onSource: (() -> Unit)?, onScrub: ((Int) -> Unit)?, offset: Int?,
    onDialog: (HudDialog) -> Unit, onGoLive: (() -> Unit)?, onJumpBack: (() -> Unit)?,
    onFavorite: (() -> Unit)?, favorite: Boolean, onPrevious: (() -> Unit)?, onOther: (() -> Unit)?,
    onEngine: (() -> Unit)?, onPip: (() -> Unit)?, onAudioOnly: (() -> Unit)?,
    onMultiview: (() -> Unit)?, onRecord: (() -> Unit)?, recording: Boolean,
    onInfo: (() -> Unit)?, infoOn: Boolean, onReport: (() -> Unit)?,
    sleepRunning: Boolean, speedLabel: String, modifier: Modifier = Modifier,
) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val qualities by player.videoQualities.collectAsStateWithLifecycle()
    val primary = primaryFocus ?: remember { FocusRequester() }
    val timeline = seekFocus ?: remember { FocusRequester() }
    val menuReady = remember { booleanArrayOf(false) }
    LaunchedEffect(toolsExpanded) {
        // The HUD owns initial focus (including hidden Left/Right rewind reveals).
        // Only an actual menu open/close moves focus to its replacement control.
        if (menuReady[0]) runCatching { (if (toolsExpanded) primary else timeline).requestFocus() }
        menuReady[0] = true
    }
    var logoAvailable by androidx.compose.runtime.remember(meta.logoUrl, meta.title) { androidx.compose.runtime.mutableStateOf(false) }
    LiveHudSize {
        CompositionLocalProvider(LocalLiveTimelineUi provides LiveTimelineUi(timeline, if (toolsExpanded) primary else timeline, playing,
            { player.togglePlayPause() }, onScrub)) {
            Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelLogo(meta.logoUrl, meta.title, 72, onAvailable = { logoAvailable = it })
                    Text(listOfNotNull(meta.subtitle?.trim()?.removePrefix("#"), meta.title.takeUnless { logoAvailable })
                        .joinToString(stringResource(R.string.content_metadata_separator)),
                        style = stageText(25, 600), color = hudInk, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    LiveStateBadge(offset)
                }
                match?.invoke()
                if (info != null) info() else LiveProgrammeInfo(null)
                Row(Modifier.fillMaxWidth().focusProperties { up = timeline },
                    horizontalArrangement = if (toolsExpanded) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (toolsExpanded) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).background(hudShade(0.65f), RoundedCornerShape(16.dp))
                            .verticalScroll(rememberScrollState()).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiveIcon(OwnTVIcon.VOLUME_HIGH, stringResource(R.string.player_tool_volume), Modifier.focusRequester(primary).onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) { onToolsExpandedChange(false); true } else false
                            }) { onDialog(HudDialog.VOLUME) }
                            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.player_tool_speed_value, speedLabel), { onDialog(HudDialog.SPEED) }, height = 58.mpx, textSize = 20)
                            LiveIcon(OwnTVIcon.SUBTITLE, stringResource(R.string.player_tool_subtitles)) { onDialog(HudDialog.SUBS) }
                            LiveIcon(OwnTVIcon.AUDIO, stringResource(R.string.player_tool_audio)) { onDialog(HudDialog.AUDIO) }
                            onFavorite?.let { LiveIcon(OwnTVIcon.FAVORITE, stringResource(R.string.player_tool_favorite), active = favorite, action = it) }
                            onSource?.let { SourceChip(sourceLabel, it) }
                            if (onGoLive != null && onScrub != null && (offset ?: 0) > 1)
                                LiveIcon(OwnTVIcon.LIVE_DOT, stringResource(R.string.player_go_live), action = onGoLive)
                            onJumpBack?.let { LiveIcon(OwnTVIcon.CATCHUP, stringResource(R.string.player_tool_catchup), action = it) }
                            onPrevious?.let { LiveIcon(OwnTVIcon.HISTORY, stringResource(R.string.player_previous_channel), action = it) }
                            onOther?.let { LiveIcon(OwnTVIcon.LIST, stringResource(R.string.player_channel_version), action = it) }
                            onEngine?.let { LiveIcon(OwnTVIcon.VIDEO, stringResource(R.string.player_tool_engine), action = it) }
                            LiveIcon(OwnTVIcon.ASPECT, stringResource(R.string.player_tool_aspect)) { onDialog(HudDialog.ZOOM) }
                            if (qualities.isNotEmpty()) LiveIcon(OwnTVIcon.VIDEO, stringResource(R.string.player_tool_quality)) { onDialog(HudDialog.QUALITY) }
                            onPip?.let { LiveIcon(OwnTVIcon.PIP, stringResource(R.string.player_tool_mini), action = it) }
                            onAudioOnly?.let { LiveIcon(OwnTVIcon.HEADPHONES, stringResource(R.string.player_tool_audio_only), action = it) }
                            onMultiview?.let { LiveIcon(OwnTVIcon.MULTIVIEW, stringResource(R.string.multiview_button), action = it) }
                            onRecord?.let { LiveIcon(OwnTVIcon.LIVE_TV, stringResource(if (recording) R.string.recording_stop else R.string.recording_record), active = recording, action = it) }
                            LiveIcon(OwnTVIcon.BEDTIME, stringResource(R.string.player_sleep_timer), active = sleepRunning) { onDialog(HudDialog.SLEEP_TIMER) }
                            onInfo?.let { LiveIcon(OwnTVIcon.INFO, stringResource(R.string.player_tool_info), active = infoOn, action = it) }
                            if (infoOn) onReport?.let { LiveIcon(OwnTVIcon.SHARE, stringResource(R.string.player_tool_report), action = it) }
                        }
                    } else {
                        Text(stringResource(R.string.player_options_down_hint), style = stageText(18, 500), color = hudInk.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String?, action: () -> Unit) = tv.own.owntv.ui.stage.StageButton(
    label ?: stringResource(R.string.player_channel_version), action, height = 58.mpx, textSize = 20)


/** Labels stay available to accessibility without expanding buttons on focus. */
@Composable
private fun LiveIcon(icon: OwnTVIcon, label: String, modifier: Modifier = Modifier, active: Boolean = false, action: () -> Unit) {
    tv.own.owntv.ui.stage.StageButton(label, action, modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        height = 48.mpx, textSize = 20, icon = icon, iconFilled = active, tinted = active)
}
