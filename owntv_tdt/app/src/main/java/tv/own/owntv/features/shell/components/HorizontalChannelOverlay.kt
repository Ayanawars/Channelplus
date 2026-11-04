package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/** A television-style horizontal channel strip; video and tuner stay untouched beneath it.
 * Only the first LEFT channel overlay uses this; RIGHT history keeps its existing design.
 */
@Composable
fun HorizontalChannelOverlay(
    channels: List<ChannelEntity>, currentId: Long?,
    nowProgrammes: Map<Long, XtEpgEntry>, nowPlaying: Map<Long, String>,
    showNumbers: Boolean, onSelect: (ChannelEntity) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    var focused by remember(channels, currentId) { mutableIntStateOf(first) }
    val state = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val time = rememberSystemTimeFormatter()
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(10_000); wallNow = System.currentTimeMillis() }
    }
    LaunchedEffect(first) {
        if (channels.isNotEmpty()) {
            state.scrollToItem((first - 2).coerceAtLeast(0))
            runCatching { initialFocus.requestFocus() }
        }
    }
    BackHandler { onDismiss() }
    val iceBlue = Color(0xFF39B9FA)
    val pale = Color(0xFFD5DAE5)
    val selected = channels.getOrNull(focused)
    Box(modifier.fillMaxSize()) {
        // Retain the live picture — overlay only the top and lower-third chrome.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .55f), Color.Transparent, Color.Transparent,
            Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .9f),
        ))))
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 52.mpx, top = 42.mpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
        ) {
            Text(stringResource(R.string.common_nav_live_tv), color = iceBlue, style = stageText(24, 800))
            Text("•", color = pale, style = stageText(20, 600))
            // The radio section uses the same strip; source selection remains with the existing app navigation.
            Text(stringResource(R.string.channelplus_radio_heading), color = pale, style = stageText(22, 600))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 50.mpx),
            verticalArrangement = Arrangement.spacedBy(18.mpx),
        ) {
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxWidth().height(122.mpx),
                horizontalArrangement = Arrangement.spacedBy(8.mpx),
                contentPadding = PaddingValues(horizontal = 42.mpx, vertical = 5.mpx),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { i, channel ->
                    val current = channel.id == currentId
                    StageSurface(
                        onClick = { onSelect(channel) },
                        radius = 10.mpx,
                        modifier = Modifier.width(270.mpx).fillMaxHeight()
                            .then(if (i == first) Modifier.focusRequester(initialFocus) else Modifier)
                            .onFocusChanged { if (it.hasFocus) {
                                focused = i
                                scope.launch { state.animateScrollToItem((i - 2).coerceAtLeast(0)) }
                            } },
                    ) { active ->
                        Row(
                            Modifier.fillMaxSize()
                                .background(if (active) iceBlue.copy(alpha = .2f) else Color.Black.copy(alpha = .5f))
                                .then(if (active) Modifier.border(BorderStroke(2.mpx, iceBlue), RoundedCornerShape(10.mpx)) else Modifier)
                                .padding(horizontal = 14.mpx),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.mpx),
                        ) {
                            ChannelLogoTile(channel.displayLogoUrl, Modifier.size(64.mpx), fill = Color.Transparent) {
                                Text(channel.number?.toString().orEmpty(), style = stageText(20, 600), color = Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                                Text(ProviderTags.parse(channel.name).name,
                                    style = stageText(19, 700), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (current) Text(stringResource(R.string.shell_now_playing),
                                    style = stageText(15, 600), color = iceBlue, maxLines = 1)
                                else if (showNumbers) Text(channel.number?.toString().orEmpty(),
                                    style = stageText(15, 500), color = pale)
                            }
                        }
                    }
                }
            }
            // A now-playing EPG card for the selected channel and its two neighbors.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 42.mpx),
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                verticalAlignment = Alignment.Top,
            ) {
                listOf(focused - 1, focused, focused + 1).forEach { index ->
                    val ch = channels.getOrNull(index)
                    val programme = ch?.let { nowProgrammes[it.id] }
                    Column(
                        Modifier.weight(1f).height(155.mpx)
                            .background(Color.Black.copy(alpha = if (index == focused) .75f else .45f), RoundedCornerShape(10.mpx))
                            .padding(16.mpx),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.mpx),
                    ) {
                        if (ch != null) {
                            Text(programme?.title ?: nowPlaying[ch.id] ?: ProviderTags.parse(ch.name).name,
                                style = stageText(if (index == focused) 21 else 18, 700),
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val valid = programme != null && programme.stopMs > programme.startMs
                            Text(if (valid) "${time(programme!!.startMs)} – ${time(programme.stopMs)}"
                                else ProviderTags.parse(ch.name).name,
                                style = stageText(16, 400), color = pale)
                            if (valid) {
                                StageProgress(((wallNow - programme!!.startMs).toFloat() /
                                    (programme.stopMs - programme.startMs)).coerceIn(0f, 1f),
                                    Modifier.fillMaxWidth(), flat = true)
                            }
                            if (index == focused && programme?.description?.isNotBlank() == true) {
                                Text(programme.description.orEmpty(), color = pale,
                                    style = stageText(15, 400), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            // Controls remain native: LEFT/RIGHT select, OK tune, BACK returns to picture.
            Row(Modifier.fillMaxWidth().padding(horizontal = 60.mpx), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◀  ▶", style = stageText(18, 500), color = pale)
                Text("OK", style = stageText(18, 700), color = iceBlue)
                Text(stringResource(R.string.common_back), style = stageText(18, 500), color = pale)
            }
        }
    }
}