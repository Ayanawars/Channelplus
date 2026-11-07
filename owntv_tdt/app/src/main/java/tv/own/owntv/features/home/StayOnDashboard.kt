package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.metadata.rememberExternalMetadata
import coil3.compose.AsyncImage
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * StayON+ landing screen. This is a browse-only layer: no video surface, no auto-tune,
 * and no changes to the existing live player / HbbTV / LOVEStv pipeline.
 *
 * Service integrations intentionally remain visible but NOT clickable until wired.
 */
private val Ink = Color.Black
private val Panel = Color(0xFF141414)
private val Muted = Color(0xFFBDBCB2)
private val White = Color(0xFFFFF8EB)
private val Yellow = Color(0xFFF4F29A)

private enum class StayTab { TV, RADIO, MUSIC, MOVIES, SERIES, APPS }

@Composable
fun StayOnDashboard(
    vm: HomeViewModel,
    onWatchChannel: (Long) -> Unit,
    onOpenTv: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenMatch: (tv.own.owntv.core.sports.model.Match) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(StayTab.TV) }
    val firstFocus = remember { FocusRequester() }
    val sportsVm = koinViewModel<tv.own.owntv.features.sports.SportsViewModel>()
    val matches by sportsVm.homeRow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier.fillMaxSize()
            .background(Ink)
            .padding(start = 34.dp, end = 34.dp, top = 22.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stayon_wordmark), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
                Text(stringResource(R.string.stayon_plus), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Yellow)
            }
            Spacer(Modifier.width(38.dp))
            val tabs = listOf(
                StayTab.TV to stringResource(R.string.stayon_tv),
                StayTab.RADIO to stringResource(R.string.stayon_nav_radio),
                StayTab.MUSIC to stringResource(R.string.stayon_music),
                StayTab.MOVIES to stringResource(R.string.stayon_movies),
                StayTab.SERIES to stringResource(R.string.stayon_series),
                StayTab.APPS to stringResource(R.string.stayon_nav_apps),
            )
            tabs.forEach { (value, label) ->
                val callback: () -> Unit = {
                    when (value) {
                        StayTab.TV, StayTab.RADIO, StayTab.MUSIC, StayTab.APPS -> tab = value
                        StayTab.MOVIES -> onOpenMovies()
                        StayTab.SERIES -> onOpenSeries()
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 20.dp)) {
                    StayTextAction(
                        label = label,
                        selected = tab == value,
                        onClick = callback,
                        modifier = if (value == StayTab.TV) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.width(35.dp).height(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (tab == value) Yellow else Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            StayIconAction(OwnTVIcon.SEARCH, onOpenSearch)
            Spacer(Modifier.width(10.dp))
            StayIconAction(OwnTVIcon.SETTINGS, onOpenSettings)
            Spacer(Modifier.width(16.dp))
            val clockFormat = stringResource(R.string.stayon_time_format)
            val clock by produceState(SimpleDateFormat(clockFormat, Locale.getDefault()).format(Date()), clockFormat) {
                while (true) {
                    value = SimpleDateFormat(clockFormat, Locale.getDefault()).format(Date())
                    delay(30_000L)
                }
            }
            Text(clock, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        when (tab) {
            StayTab.TV -> TvDashboard(
                state = state,
                onWatchChannel = onWatchChannel,
                onOpenTv = onOpenTv,
                onOpenGuide = onOpenGuide,
                match = matches.firstOrNull(),
                onOpenMatch = { match -> onOpenMatch(match) },
                modifier = Modifier.weight(1f),
            )
            StayTab.RADIO -> StayOnRadioDashboard(
                onManage = onOpenSettings, modifier = Modifier.weight(1f))
            StayTab.MUSIC -> MusicDashboard(modifier = Modifier.weight(1f))
            StayTab.APPS -> StayOnAppsDashboard(modifier = Modifier.weight(1f))
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stayon_coming_soon), fontSize = 27.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun StayTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF474441) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused || selected) White else Muted, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun StayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Yellow else White)
            .border(if (focused) 2.dp else 0.dp, if (focused) White else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StayIconAction(icon: OwnTVIcon, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color(0xFF303238) else Color.Transparent)
            .clickable(onClick = onClick)
            .focusable()
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        OwnTVIcon(icon, tint = if (focused) Yellow else White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TvDashboard(
    state: HomeUiState,
    onWatchChannel: (Long) -> Unit,
    onOpenTv: () -> Unit,
    onOpenGuide: () -> Unit,
    match: tv.own.owntv.core.sports.model.Match?,
    onOpenMatch: (tv.own.owntv.core.sports.model.Match) -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = state.recentLive.firstOrNull()
    val favorite = state.favoriteLive.firstOrNull()
    val now = System.currentTimeMillis()
    val guide = last?.let { state.recentGuide.programmes[it.id]?.firstOrNull { p -> p.startMs <= now && p.stopMs > now } }
    val metadata = rememberExternalMetadata(guide?.title)
    val artwork = metadata?.tvdbArtwork ?: metadata?.posterUrl
    val timeFormat = stringResource(R.string.stayon_time_format)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(
                Modifier.fillMaxSize()
                    .background(Ink),
            ) {
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.78f).fillMaxHeight(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Ink, .28f to Ink.copy(alpha = .94f),
                                .60f to Ink.copy(alpha = .38f), 1f to Color.Transparent,
                            ),
                        ),
                    )
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .28f)))),
                    )
                }
                Column(
                    Modifier.fillMaxSize().padding(start = 14.dp, top = 12.dp, bottom = 28.dp, end = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.stayon_last_channel), letterSpacing = 3.sp, fontSize = 12.sp, color = Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(.74f)) {
                        if (last != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!last.displayLogoUrl.isNullOrBlank()) {
                                    AsyncImage(model = last.displayLogoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(last.name, fontSize = 20.sp, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(guide?.title ?: stringResource(R.string.stayon_tv_hint), fontSize = if (guide != null) 36.sp else 28.sp, lineHeight = 40.sp, color = White, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(stringResource(R.string.stayon_no_history), fontSize = 30.sp, color = White, fontWeight = FontWeight.Bold)
                        }
                        if (guide != null) {
                            val startTime = remember(guide.startMs, timeFormat) { SimpleDateFormat(timeFormat, Locale.getDefault()).format(Date(guide.startMs)) }
                            val endTime = remember(guide.stopMs, timeFormat) { SimpleDateFormat(timeFormat, Locale.getDefault()).format(Date(guide.stopMs)) }
                            Text(stringResource(R.string.stayon_epg_schedule, startTime, endTime), fontSize = 17.sp, color = White)
                            val progress = ((now - guide.startMs).toFloat() / (guide.stopMs - guide.startMs).coerceAtLeast(1L)).coerceIn(0f,1f)
                            Box(Modifier.fillMaxWidth(.68f).height(5.dp).background(Color(0xFF444448), RoundedCornerShape(9.dp))) {
                                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Yellow, RoundedCornerShape(9.dp)))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (last != null) {
                            StayButton(stringResource(R.string.stayon_watch), { onWatchChannel(last.id) })
                        } else {
                            StayButton(stringResource(R.string.stayon_browse_tv), onOpenTv)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StayButton(stringResource(R.string.stayon_guide), onOpenGuide)
            favorite?.let { channel ->
                StayButton(channel.name, { onWatchChannel(channel.id) })
            }
            match?.let { event -> StayButton(event.title, { onOpenMatch(event) }) }
        }
        Spacer(Modifier.height(20.dp))
        StayDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(91.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Image(
                    painterResource(R.drawable.stayon_lovestv_logo),
                    contentDescription = stringResource(R.string.stayon_lovestv_content_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(175.dp).height(50.dp),
                )
                Text(stringResource(R.string.stayon_lovestv_tagline), fontSize = 12.sp, color = Muted, maxLines = 1)
            }
            StayVerticalDivider()
            StayBroadcaster("2da80e502149f5943de87660.svg", stringResource(R.string.stayon_mediaset), stringResource(R.string.stayon_telecinco), Modifier.weight(1f))
            StayVerticalDivider()
            StayBroadcaster("ed365a20408d10ce8ad65c1e.svg", stringResource(R.string.stayon_rtve), stringResource(R.string.stayon_la1), Modifier.weight(1f))
            StayVerticalDivider()
            StayBroadcaster("dcd4717ba694dc4af37f8610.svg", stringResource(R.string.stayon_atresmedia), stringResource(R.string.stayon_antena3), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StayBroadcaster(asset: String, group: String, channel: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        AsyncImage(
            model = "file:///android_asset/channel-logos/wikimedia/$asset",
            contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(group, color = Muted, fontSize = 11.sp, maxLines = 1)
            Text(channel, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun StayDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF414147)))
}

@Composable
private fun StayVerticalDivider() {
    Box(Modifier.width(1.dp).height(58.dp).background(Color(0xFF414147)))
}

/**
 * Real radio entries from dedicated Radio IPTV sources. TV playlists are never scanned here.
 * Uses the same in-app audio controller as Sources > Radio IPTV.
 */
@Composable
private fun StayOnRadioDashboard(onManage: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember(context) { tv.own.owntv.features.settings.StayonRadioLibrary.shared(context) }
    val stations by library.stations.collectAsStateWithLifecycle()
    var tunerStation by remember { mutableStateOf<tv.own.owntv.features.settings.StayonRadioStation?>(null) }
    val playing by tv.own.owntv.features.settings.RadioAudioPlayer.playingStationId.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stayon_radio_stations), fontSize = 26.sp,
                fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
            StayTextAction(
                stringResource(R.string.stayon_radio_manage), onClick = onManage,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (stations.isEmpty()) {
            Text(stringResource(R.string.stayon_radio_empty), fontSize = 19.sp, color = Muted)
        } else LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stations, key = { it.id }) { station ->
                val active = playing == station.id
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) Yellow else Panel)
                        .clickable {
                            if (station.tunerChannel != null) tunerStation = station
                            else if (active) tv.own.owntv.features.settings.RadioAudioPlayer.stop()
                            else tv.own.owntv.features.settings.RadioAudioPlayer.play(station)
                        }
                        .focusable()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!station.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = station.logo, contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp),
                        )
                    } else {
                        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.AUDIO, if (focused) Ink else Yellow, Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(station.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = if (focused) Ink else White, maxLines = 1)
                        Text(
                            if (active) stringResource(R.string.stayon_radio_stop)
                            else stringResource(R.string.stayon_radio_listen),
                            fontSize = 14.sp, color = if (focused) Ink else Muted,
                        )
                        if (station.tunerChannel != null) {
                            Text(stringResource(R.string.stayon_radio_tdt), fontSize = 13.sp,
                                color = if (focused) Ink else Muted)
                        }
                    }
                }
            }
        }
    }
    tunerStation?.let { station ->
        tv.own.owntv.features.settings.TunerRadioPlayback(station) { tunerStation = null }
    }
}

/** User-launchable Android TV apps, not mockup placeholders. */
@Composable
private fun StayOnAppsDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = remember(context) {
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }
    val apps by produceState(initialValue = emptyList<android.content.pm.LauncherActivityInfo>(), context) {
        val prefs = context.getSharedPreferences("stayon_apps_settings", Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet("hidden", emptySet()).orEmpty()
        val favorites = prefs.getStringSet("favorites", emptySet()).orEmpty()
        val includeNonTv = prefs.getBoolean("show_non_tv", false)
        value = withContext(Dispatchers.IO) {
            runCatching { launcher.getActivityList(null, Process.myUserHandle()) }
                .getOrDefault(emptyList())
                .filter { it.componentName.packageName != context.packageName }
                .filter { it.componentName.packageName !in hidden }
                .filter { includeNonTv || context.packageManager.getLeanbackLaunchIntentForPackage(it.componentName.packageName) != null }
                .distinctBy { it.componentName.packageName }
                .sortedWith(compareBy<android.content.pm.LauncherActivityInfo> {
                    if (it.componentName.packageName in favorites) 0 else 1
                }.thenBy { it.label.toString().lowercase() })
        }
    }
    Column(modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.stayon_apps_installed), fontSize = 26.sp,
            fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.height(16.dp))
        if (apps.isEmpty()) {
            Text(stringResource(R.string.stayon_apps_empty), fontSize = 19.sp, color = Muted)
        } else LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.componentName.flattenToString() }) { app ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) Yellow else Panel)
                        .clickable {
                            runCatching {
                                launcher.startMainActivity(app.componentName, app.user, null, null)
                            }
                        }
                        .focusable()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label.toString(), fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold, color = if (focused) Ink else White)
                }
            }
        }
    }
}

@Composable
private fun MusicDashboard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Ink)) {
            Image(
                painterResource(R.drawable.stayon_music_hero), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            )
            // Keep the content region and all outside margins absolute black.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Ink, .40f to Ink, .62f to Color.Transparent,
            )))
            Column(
                Modifier.fillMaxHeight().fillMaxWidth(.57f).padding(start = 18.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.stayon_your_station), fontSize = 12.sp, letterSpacing = 3.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.size(88.dp).border(1.dp, Muted, CircleShape),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.stayon_urban_number), fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = White)
                        Text(stringResource(R.string.stayon_urban_brand), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White)
                        Box(Modifier.width(43.dp).height(3.dp).background(Yellow, CircleShape))
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(stringResource(R.string.stayon_urban), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = White)
                        Text(stringResource(R.string.stayon_music_pending), fontSize = 14.sp, color = Muted)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.stayon_now_playing), fontSize = 12.sp, letterSpacing = 3.sp, color = Muted)
                    // Do not present the mockup's song as real live metadata.
                    Text(stringResource(R.string.stayon_radio_pending), fontSize = 20.sp, color = White, maxLines = 2)
                }
                // Radio integration is pending: preserve the CTA shape without a dead focus target.
                Box(Modifier.clip(RoundedCornerShape(28.dp)).background(White).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.stayon_listen), fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        // The music brands form one clean black strip, NOT three colored tiles.
        // Keep the provider logos non-focusable until their services are integrated.
        Column(
            Modifier.fillMaxWidth()
                .background(Color.Black)
                .padding(top = 2.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF48484B)))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.stayon_music_all), fontSize = 18.sp, color = White)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(92.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The three original wordmarks have very different built-in
                // text-to-image ratios. Normalize their *visible lettering*
                // (not their canvas height) to a similar ~26dp on Android TV.
                StayMusicWordmark(
                    R.drawable.stayon_spotify,
                    stringResource(R.string.stayon_spotify),
                    42.dp,
                    Modifier.weight(1f),
                )
                StayMusicSeparator()
                StayMusicWordmark(
                    R.drawable.stayon_youtube_music,
                    stringResource(R.string.stayon_youtube_music),
                    49.dp,
                    Modifier.weight(1f),
                )
                StayMusicSeparator()
                StayMusicWordmark(
                    R.drawable.stayon_amazon_music,
                    stringResource(R.string.stayon_amazon_music),
                    42.dp,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StayMusicWordmark(
    logo: Int,
    label: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(logo),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.86f).height(height),
            colorFilter = if (logo == R.drawable.stayon_amazon_music) androidx.compose.ui.graphics.ColorFilter.tint(White) else null,
        )
    }
}

@Composable
private fun StayMusicSeparator() {
    Box(
        Modifier
            .width(1.dp)
            .height(42.dp)
            .background(Color(0xFF66666A)),
    )
}
