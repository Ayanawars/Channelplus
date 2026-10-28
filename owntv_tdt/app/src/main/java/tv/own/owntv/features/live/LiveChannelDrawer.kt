package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.theme.StageAccent
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private val DrawerIvory = Color(0xFFF1E3BF)
private val DrawerMuted = Color(0xFFC4C6CC)

/** A single video owner behind the drawer, including the native TDT surface. */
@Composable
internal fun LiveDrawerBackdrop(engine: LivePreviewEngine, channel: ChannelEntity?, enabled: Boolean, blocked: Boolean) {
    val state by engine.state.collectAsStateWithLifecycle()
    val playing = enabled && state != LivePreviewEngine.State.ERROR && state != LivePreviewEngine.State.IDLE
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (playing) ExoPreviewSurface(engine, Modifier.fillMaxSize())
        else if (channel != null) ChannelLogoTile(channel.displayLogoUrl, Modifier.size(140.mpx), fill = Color.Transparent) {}
        if (enabled && state == LivePreviewEngine.State.LOADING) OwnTVSpinner(sizeDp = 28)
        if (blocked && !playing) Text(stringResource(R.string.content_preview_single_stream), color = Color.White, style = stageText(18, 500))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.87f))))
    }
}

@Composable
internal fun LiveDrawerRow(
    channel: ChannelEntity, name: String, title: String?, favorite: Boolean, showNumber: Boolean,
    onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier,
) {
    StageSurface(
        onClick = onClick, onLongClick = onLongClick, radius = 18.mpx,
        accent = StageAccent(DrawerIvory, Color(0xFF151515), DrawerIvory),
        modifier = modifier.fillMaxWidth().height(78.mpx).onFocusChanged { if (it.hasFocus) onFocus() },
    ) { focused ->
        val ink = if (focused) Color(0xFF151515) else Color.White
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.mpx), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            if (showNumber) Text(channel.number?.toString().orEmpty(), style = stageText(21, 500), color = ink, modifier = Modifier.width(30.mpx))
            ChannelLogoTile(channel.displayLogoUrl, Modifier.size(58.mpx), fill = Color.Transparent) {
                OwnTVIcon(OwnTVIcon.LIVE_TV, ink, Modifier.size(28.mpx))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = stageText(23, 600), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!title.isNullOrBlank()) Text(title, style = stageText(18, 400), color = if (focused) ink.copy(alpha = 0.8f) else DrawerMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (favorite) OwnTVIcon(OwnTVIcon.FAVORITE, ink, Modifier.size(18.mpx))
        }
    }
}

/** Programme details stay on the video; the guide and its schedule navigation remain separate. */
@Composable
internal fun LiveDrawerProgramme(
    channel: ChannelEntity?, nowNext: EpgNowNext?, previewEngine: LivePreviewEngine,
    scheduleFocus: FocusRequester, onBackToList: () -> Unit, onOpenProgramme: (XtEpgEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = rememberSystemTimeFormatter()
    val chips by previewEngine.streamChips.collectAsStateWithLifecycle()
    val separator = stringResource(R.string.player_metadata_separator)
    val instant by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    // Reading the clock also refreshes programme progress while browsing one channel.
    val now = nowNext?.now
    val external = tv.own.owntv.ui.metadata.rememberExternalMetadata(now?.title, nowNext?.nowDetails)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
            if (channel != null) ChannelLogoTile(channel.displayLogoUrl, Modifier.size(72.mpx), fill = Color.Transparent) {}
            Column(Modifier.weight(1f)) {
                Text(now?.title ?: channel?.let { ProviderTags.parse(it.name).name }.orEmpty(), style = stageText(29, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((listOfNotNull(channel?.let { ProviderTags.parse(it.name).name }) + chips.filterNot { it.contains(':') || it.endsWith("Mbps") }).joinToString(separator),
                    style = stageText(18, 400), color = DrawerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(time(instant), style = stageText(22, 500), color = Color.White)
        }
        tv.own.owntv.ui.metadata.ExternalMetadataBadges(external, color = DrawerMuted)
        if (now != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                Text(time(now.startMs), style = stageText(18, 400), color = DrawerMuted)
                StageProgress(((instant - now.startMs).toFloat() / (now.stopMs - now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.weight(1f), flat = true)
                Text(time(now.stopMs), style = stageText(18, 400), color = DrawerMuted)
            }
            (now.description?.takeIf { it.isNotBlank() } ?: external?.overview ?: external?.tvdbOverview)?.let {
                Text(it, style = stageText(19, 400), color = DrawerMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        nowNext?.next?.let { next ->
            StageSurface(onClick = { onOpenProgramme(next) }, radius = 10.mpx,
                modifier = Modifier.focusRequester(scheduleFocus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { onBackToList(); true } else false
                }) { focused ->
                Text(stringResource(R.string.live_drawer_next_programme, time(next.startMs), next.title), style = stageText(18, 400), color = if (focused) Color.Black else DrawerMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.mpx))
            }
        }
        StageKeyHints(listOf(stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options)))
    }
}
