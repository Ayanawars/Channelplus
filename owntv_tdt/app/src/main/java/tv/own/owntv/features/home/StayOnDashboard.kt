package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.features.settings.StayonMusicApp
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StayonBroadcaster
import tv.own.owntv.core.settings.StayonHomeLayout
import tv.own.owntv.core.settings.StayonHomeTab
import tv.own.owntv.core.settings.StayonHomeWidget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
 */
private val Ink = Color.Black
private val Panel = Color(0xFF141414)
private val Muted = Color(0xFFBDBCB2)
private val White = Color(0xFFFFF8EB)
private val Yellow = Color(0xFFF4F29A)

@Composable
fun StayOnDashboard(
    vm: HomeViewModel,
    onWatchChannel: (Long) -> Unit,
    onOpenTv: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMatch: (tv.own.owntv.core.sports.model.Match) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val repo = koinInject<SettingsRepository>()
    val saved by repo.stayonHomeLayout.collectAsStateWithLifecycle(StayonHomeLayout())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<StayonHomeLayout?>(null) }
    val layout = preview ?: saved
    var tab by remember { mutableStateOf(StayonHomeTab.TV) }
    LaunchedEffect(saved.startup) { tab = saved.startup }
    val shownTab = tab.takeIf { it in layout.tabs } ?: layout.startup
    val firstFocus = remember { FocusRequester() }
    val sportsVm = koinViewModel<tv.own.owntv.features.sports.SportsViewModel>()
    val matches by sportsVm.homeRow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val density = LocalDensity.current
    val scale = layout.scale / 100f
    Box(modifier.fillMaxSize().background(Ink).padding(horizontal = 24.dp, vertical = 18.dp)) {
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Column(Modifier.fillMaxSize()) {
                StayHomeHeader(layout, shownTab, firstFocus, { tab = it }, onOpenSettings,
                    onOpenSearch, onEdit = { editing = true })
                Spacer(Modifier.height(24.dp))
                when (shownTab) {
                    StayonHomeTab.TV -> TvDashboard(
                        state = state,
                        layout = layout,
                        onWatchChannel = onWatchChannel,
                        onOpenTv = onOpenTv,
                        onOpenGuide = onOpenGuide,
                        match = matches.firstOrNull(),
                        onOpenMatch = { match -> onOpenMatch(match) },
                        modifier = Modifier.weight(1f),
                    )
                    StayonHomeTab.RADIO -> StayOnRadioDashboard(
                        onManage = onOpenSettings, modifier = Modifier.weight(1f))
                    StayonHomeTab.MUSIC -> MusicDashboard(modifier = Modifier.weight(1f))
                    StayonHomeTab.APPS -> StayOnAppsDashboard(modifier = Modifier.weight(1f))

                }
            }
        }
    }
    if (editing) {
        StayonHomeEditor(saved, onPreview = { preview = it }, onSave = { result ->
            scope.launch { repo.setStayonHomeLayout(result); editing = false; preview = null }
        }, onDismiss = { editing = false; preview = null })
    }
}

@Composable
private fun StayHomeHeader(
    layout: StayonHomeLayout,
    shownTab: StayonHomeTab,
    firstFocus: FocusRequester,
    onTab: (StayonHomeTab) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
) {
    val clockFormat = stringResource(R.string.stayon_time_format)
    val clock by produceState(SimpleDateFormat(clockFormat, Locale.getDefault()).format(Date()), clockFormat) {
        val formatter = SimpleDateFormat(clockFormat, Locale.getDefault())
        while (true) {
            value = formatter.format(Date())
            delay(30_000L)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stayon_wordmark), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1)
            Text(stringResource(R.string.stayon_plus), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Yellow)
        }
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                layout.tabs.forEachIndexed { index, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StayTextAction(
                            label = homeTabLabel(value),
                            selected = shownTab == value,
                            onClick = { onTab(value) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.width(35.dp).height(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (shownTab == value) Yellow else Color.Transparent),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StayIconAction(OwnTVIcon.SEARCH, onSearch)
            StayIconAction(OwnTVIcon.SETTINGS, onSettings, onLongClick = onEdit)
            Spacer(Modifier.width(10.dp))
            Text(clock, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
private fun StayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Yellow else Panel)
            .border(if (focused) 2.dp else 1.dp, if (focused) White else Muted.copy(alpha = .42f), shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 24.dp, vertical = if (large) 20.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = if (focused) Ink else White, fontWeight = FontWeight.Bold)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StayIconAction(icon: OwnTVIcon, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color(0xFF303238) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
    layout: StayonHomeLayout,
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
    val artwork = guide?.imageUrl ?: metadata?.tvdbArtwork ?: metadata?.posterUrl
    val timeFormat = stringResource(R.string.stayon_time_format)

    val showBroadcasters = StayonHomeWidget.BROADCASTERS !in layout.hidden
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val heroMinHeight = minOf(maxHeight * .62f, if (StayonHomeWidget.LAST_CHANNEL in layout.large) 330.dp else 245.dp)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (StayonHomeWidget.LAST_CHANNEL in layout.hidden) {
                    StayButton(stringResource(R.string.stayon_browse_tv), onOpenTv)
                }
                val visibleWidgets = layout.order.filterNot { it == StayonHomeWidget.BROADCASTERS || it in layout.hidden }
                visibleWidgets.forEach { widget ->
                    val large = widget in layout.large
                    when (widget) {
                        StayonHomeWidget.LAST_CHANNEL -> Box(Modifier.fillMaxWidth().background(Ink)) {
                            Box(Modifier.matchParentSize()) {
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
                            }
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = heroMinHeight)
                                    .padding(start = 14.dp, top = 12.dp, bottom = 16.dp, end = 14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.stayon_last_channel), letterSpacing = 3.sp, fontSize = 12.sp, color = Muted)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(.74f).padding(top = 10.dp),
                                ) {
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
                                    if (last != null) {
                                        StayButton(stringResource(R.string.stayon_watch), { onWatchChannel(last.id) })
                                    } else {
                                        StayButton(stringResource(R.string.stayon_browse_tv), onOpenTv)
                                    }
                                }
                            }
                        }
                        StayonHomeWidget.GUIDE -> StayButton(stringResource(R.string.stayon_guide), onOpenGuide, large = large)
                        StayonHomeWidget.FAVORITE -> favorite?.let { channel ->
                            StayButton(channel.name, { onWatchChannel(channel.id) }, large = large)
                        }
                        StayonHomeWidget.SPORTS -> match?.let { event ->
                            StayButton(event.title, { onOpenMatch(event) }, large = large)
                        }
                        StayonHomeWidget.BROADCASTERS -> Unit // Rendered in its own footer below the scroll area.
                    }
                }
            }
        }
        if (showBroadcasters) StayBroadcasters(layout, StayonHomeWidget.BROADCASTERS in layout.large)
    }
}

@Composable
private fun StayBroadcasters(layout: StayonHomeLayout, large: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (large) 100.dp else 80.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(
                painterResource(R.drawable.stayon_lovestv_logo),
                contentDescription = stringResource(R.string.stayon_lovestv_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(.92f).height(48.dp),
                alignment = Alignment.CenterStart,
            )
            Text(stringResource(R.string.stayon_lovestv_tagline), fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        StayonBroadcaster.entries.forEach { broadcaster ->
            StayVerticalDivider()
            val image = when (broadcaster) {
                StayonBroadcaster.MEDIASET -> R.drawable.stayon_mediaset_logo
                StayonBroadcaster.RTVE -> R.drawable.stayon_rtve_logo
                StayonBroadcaster.ATRESMEDIA -> R.drawable.stayon_atresmedia_logo
            }
            val size = layout.logoSize(broadcaster) / 100f
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(image),
                    contentDescription = broadcasterLabel(broadcaster),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(.72f * size).height((if (large) 54.dp else 46.dp) * size),
                )
            }
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
    val guideStore = remember(context) { tv.own.owntv.features.settings.RadioGuideStore.shared(context) }
    val guideFeeds by guideStore.feeds.collectAsStateWithLifecycle()
    val guidePriorities by guideStore.priorities.collectAsStateWithLifecycle()
    LaunchedEffect(guideStore) { guideStore.load() }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }
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
                val (programme, guideLogo) = remember(guideFeeds, guidePriorities, station, now) {
                    val source = station.playlistId ?: station.tunerChannel?.inputId
                    val selected = guidePriorities[source].orEmpty().mapNotNull { id -> guideFeeds.firstOrNull { it.id == id } }
                    tv.own.owntv.features.settings.radioGuideMatch(selected.ifEmpty { guideFeeds }, station, now)
                }
                val logo = station.logo ?: guideLogo
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) Yellow else Panel)
                        .clickable {
                            if (station.tunerChannel != null) tunerStation = station
                            else if (active) tv.own.owntv.features.settings.RadioAudioPlayer.stop()
                            else tv.own.owntv.features.settings.RadioPlaybackService.play(context, station)
                        }
                        .focusable()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!logo.isNullOrBlank()) {
                        AsyncImage(
                            model = logo, contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp),
                        )
                    } else {
                        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.AUDIO, if (focused) Ink else Yellow, Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(station.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = if (focused) Ink else White, maxLines = 1)
                        Text(
                            programme?.title ?: if (active) stringResource(R.string.stayon_radio_stop)
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
        val ranks = prefs.getString("order", "").orEmpty().split(',').filter(String::isNotBlank)
            .withIndex().associate { it.value to it.index }
        value = withContext(Dispatchers.IO) {
            runCatching { launcher.getActivityList(null, Process.myUserHandle()) }
                .getOrDefault(emptyList())
                .filter { it.componentName.packageName != context.packageName }
                .filter { it.componentName.packageName !in hidden }
                .filter { includeNonTv || context.packageManager.getLeanbackLaunchIntentForPackage(it.componentName.packageName) != null }
                .distinctBy { it.componentName.packageName }
                .sortedWith(compareBy<android.content.pm.LauncherActivityInfo> {
                    ranks[it.componentName.packageName] ?: Int.MAX_VALUE
                }.thenBy { if (it.componentName.packageName in favorites) 0 else 1 }.thenBy { it.label.toString().lowercase() })
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
        Text(stringResource(R.string.stayon_music), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.height(24.dp))
        // The music brands form one clean black strip, NOT three colored tiles.
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
                val services = StayonMusicApp.entries
                services.forEachIndexed { index, service ->
                    if (index > 0) StayMusicSeparator()
                    StayMusicWordmark(service, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StayMusicWordmark(service: StayonMusicApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Box(modifier.fillMaxHeight()
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 0.dp, if (focused) Yellow else Color.Transparent)
        .clickable { service.open(context) }, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(service.logo),
            contentDescription = stringResource(service.label),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.86f).height(if (service == StayonMusicApp.SPOTIFY) 42.dp else 49.dp),
            colorFilter = if (service == StayonMusicApp.AMAZON) androidx.compose.ui.graphics.ColorFilter.tint(White) else null,
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
