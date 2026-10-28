package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import tv.own.owntv.ui.components.OwnTVIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.hudAccent
import tv.own.owntv.ui.theme.hudInk
import tv.own.owntv.ui.theme.stageText

internal data class LiveTimelineUi(
    val focus: FocusRequester, val down: FocusRequester, val playing: Boolean,
    val togglePlayback: () -> Unit, val scrub: ((Int) -> Unit)?,
)
internal val LocalLiveTimelineUi = staticCompositionLocalOf<LiveTimelineUi?> { null }

/** Live identity and guide stay below; only the wordmark and clock sit above the picture. */
@Composable
internal fun LivePlayerTopBar(watchingMs: Long?, modifier: Modifier = Modifier) {
    val formatTime = rememberSystemTimeFormatter()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); now = System.currentTimeMillis() } }
    Row(modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.Top) {
        Text(stringResource(R.string.tdt_app_name), style = stageText(32, 700), color = hudInk)
        Spacer(Modifier.weight(1f))
        if (watchingMs != null) PlayerClock(watchingMs) else
            Text(formatTime(now), style = stageText(30, 500), color = hudInk)
    }
}

/** Programme progress is informational. The seekable timeshift buffer stays in Options. */
@Composable
fun LiveProgrammeInfo(epg: EpgNowNext?, atMs: Long? = null, modifier: Modifier = Modifier) {
    val formatTime = rememberSystemTimeFormatter()
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); wallNow = System.currentTimeMillis() } }
    val now = atMs ?: wallNow
    val programmeNow = epg?.now
    val retained = LocalLiveProgrammeArtwork.current
    val external = if (retained != null && retained.identity == programmeArtworkIdentity(epg)) retained.metadata
        else tv.own.owntv.ui.metadata.rememberExternalMetadata(programmeNow?.title, epg?.nowDetails)
    // Artwork is scoped to this exact airing and clears synchronously on programme changes.
    val poster = external?.posterUrl ?: external?.tvdbArtwork
    val logo = external?.logoUrl
    val context = LocalContext.current
    val posterRequest = remember(poster, context) { ImageRequest.Builder(context).data(poster).crossfade(false).build() }
    val logoRequest = remember(logo, context) { ImageRequest.Builder(context).data(logo).crossfade(false).build() }
    var posterFailed by remember(poster) { mutableStateOf(false) }
    var logoFailed by remember(logo) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 680.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide && poster != null && !posterFailed) {
                AsyncImage(model = posterRequest, contentDescription = null, contentScale = ContentScale.Fit,
                    onError = { posterFailed = true },
                    modifier = Modifier.width(104.dp).height(156.dp).clip(RoundedCornerShape(5.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                programmeNow?.let { programme ->
                    val guideDetails = epg?.nowDetails
                    Text(programme.title, style = stageText(38, 700), color = hudInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    tv.own.owntv.ui.metadata.ExternalMetadataBadges(external, color = hudInk.copy(alpha = 0.8f), details = guideDetails)
                    ProgrammeMetadataLine(external, guideDetails)
                    (programme.description?.takeIf { it.isNotBlank() } ?: external?.overview ?: external?.tvdbOverview)?.let { description ->
                        Text(description, style = stageText(21, 400), color = hudInk.copy(alpha = 0.85f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (programme.stopMs > programme.startMs) {
                        val progress = ((now - programme.startMs).toDouble() / (programme.stopMs - programme.startMs)).toFloat().coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TimelinePause()
                            Text(formatTime(programme.startMs), style = stageText(20, 500), color = hudInk)
                            Spacer(Modifier.width(10.dp))
                            ProgrammeSeekTrack(progress, Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text(formatTime(programme.stopMs), style = stageText(20, 500), color = hudInk)
                        }
                        val minutesLeft = ((programme.stopMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L
                        Text(stringResource(R.string.player_programme_remaining, minutesLeft.toInt()),
                            style = stageText(18, 500), color = hudInk.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (programmeNow == null || programmeNow.stopMs <= programmeNow.startMs) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TimelinePause()
                        ProgrammeSeekTrack(1f, Modifier.weight(1f))
                    }
                }
                epg?.next?.let { next ->
                    Text(stringResource(R.string.player_programme_next, formatTime(next.startMs), next.title),
                        style = stageText(20, 500), color = hudInk.copy(alpha = 0.65f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (wide && logo != null && !logoFailed) {
                // Fit preserves the original transparent canvas; never add a plate behind title art.
                AsyncImage(model = logoRequest, contentDescription = null, contentScale = ContentScale.Fit,
                    onError = { logoFailed = true }, modifier = Modifier.width(210.dp).height(90.dp))
            }
        }
    }
}

@Composable
private fun TimelinePause() {
    val controls = LocalLiveTimelineUi.current ?: return
    val label = stringResource(if (controls.playing) R.string.live_hud_pause else R.string.live_hud_resume)
    CircleButton(if (controls.playing) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, 32,
        modifier = Modifier.focusProperties { down = controls.down; right = controls.focus }
            .semantics { contentDescription = label }, onClick = controls.togglePlayback)
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun ProgrammeSeekTrack(progress: Float, modifier: Modifier = Modifier) {
    val controls = LocalLiveTimelineUi.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.live_hud_timeline_hint)
    val focusModifier = if (controls == null) Modifier else Modifier
        .focusRequester(controls.focus).focusProperties { down = controls.down }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionLeft -> { controls.scrub?.invoke(LIVE_SCRUB_STEP_SEC); true }
                Key.DirectionRight -> { controls.scrub?.invoke(-LIVE_SCRUB_STEP_SEC); true }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { controls.togglePlayback(); true }
                Key.DirectionDown -> { controls.down.requestFocus(); true }
                else -> false
            }
        }
        .semantics { contentDescription = label }
        .focusable(interactionSource = interaction)
    Box(modifier.height(28.dp).then(focusModifier)
        .border(if (focused) 1.dp else 0.dp, if (focused) hudAccent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
        .padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(if (focused) 5.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(hudInk.copy(alpha = 0.28f))) {
            if (progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(if (focused) 5.dp else 4.dp).background(hudAccent))
        }
    }
}

@Composable
private fun ProgrammeMetadataLine(data: tv.own.owntv.core.metadata.ExternalTitleMetadata?, details: tv.own.owntv.core.parser.EpgDetails?) {
    val categories = details?.categoryList.orEmpty()
    val series = data?.type == tv.own.owntv.core.metadata.MetadataType.TV || !details?.episode.isNullOrBlank() || categories.any { it.contains("serie", true) }
    val movie = data?.type == tv.own.owntv.core.metadata.MetadataType.MOVIE || categories.any { it.contains("película", true) || it.contains("pelicula", true) || it.contains("movie", true) }
    val genre = categories.filterNot { it.lowercase() in setOf("serie", "series", "película", "pelicula", "cine", "movie", "film") }.takeIf { it.isNotEmpty() } ?: data?.genres.orEmpty()
    val parts = listOfNotNull((details?.year ?: data?.year)?.toString(),
        if (series) stringResource(R.string.programme_series) else if (movie) stringResource(R.string.programme_movie) else null) + genre.take(2).map { g ->
        when (g.lowercase()) {
            "crime" -> stringResource(R.string.programme_crime)
            "drama" -> stringResource(R.string.programme_drama)
            "comedy" -> stringResource(R.string.programme_comedy)
            "action" -> stringResource(R.string.programme_action)
            "thriller" -> stringResource(R.string.programme_thriller)
            "documentary" -> stringResource(R.string.programme_documentary)
            else -> g
        }
    }
    if (parts.isNotEmpty()) Text(parts.distinct().joinToString(stringResource(R.string.programme_separator)),
        style = stageText(18, 500), color = hudInk.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
