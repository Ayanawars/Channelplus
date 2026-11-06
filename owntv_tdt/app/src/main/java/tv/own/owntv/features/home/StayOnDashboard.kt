package tv.own.owntv.features.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
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
private val Ink = Color(0xFF08090D)
private val Panel = Color(0xFF17181D)
private val Muted = Color(0xFFA7A7AD)
private val White = Color(0xFFFAF9F6)
private val Yellow = Color(0xFFF5C927)

private enum class StayTab { TV, MUSIC, MOVIES, SERIES, APPS }

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
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(StayTab.TV) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111217), Ink, Color(0xFF09090C))))
            .padding(start = 34.dp, end = 34.dp, top = 22.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stayon_wordmark), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
                Text(stringResource(R.string.stayon_plus), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Yellow)
            }
            Spacer(Modifier.width(38.dp))
            val tabs = listOf(
                StayTab.TV to stringResource(R.string.stayon_tv), StayTab.MUSIC to stringResource(R.string.stayon_music),
                StayTab.MOVIES to stringResource(R.string.stayon_movies), StayTab.SERIES to stringResource(R.string.stayon_series), StayTab.APPS to stringResource(R.string.stayon_apps),
            )
            tabs.forEach { (value, label) ->
                val callback: () -> Unit = {
                    when (value) {
                        StayTab.TV, StayTab.MUSIC, StayTab.APPS -> tab = value
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
            StayTextAction(stringResource(R.string.stayon_search_icon), onOpenSearch)
            Spacer(Modifier.width(10.dp))
            StayTextAction(stringResource(R.string.stayon_settings_icon), onOpenSettings)
            Spacer(Modifier.width(16.dp))
            val clockFormat = stringResource(R.string.stayon_time_format)
            val clock = remember(clockFormat) { SimpleDateFormat(clockFormat, Locale.getDefault()).format(Date()) }
            Text(clock, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        when (tab) {
            StayTab.TV -> TvDashboard(
                state = state,
                onWatchChannel = onWatchChannel,
                onOpenTv = onOpenTv,
                onOpenGuide = onOpenGuide,
                modifier = Modifier.weight(1f),
            )
            StayTab.MUSIC -> MusicDashboard(modifier = Modifier.weight(1f))
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
private fun TvDashboard(
    state: HomeUiState,
    onWatchChannel: (Long) -> Unit,
    onOpenTv: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = state.recentLive.firstOrNull()
    val fav = state.favoriteLive.firstOrNull()
    val now = System.currentTimeMillis()
    val guide = last?.let { ch ->
        state.recentGuide.programmes[ch.id]?.firstOrNull { it.startMs <= now && it.stopMs > now }
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.weight(2f).fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Panel, Color(0xFF262022), Color(0xFF151317))))
                    .padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.stayon_last_channel), letterSpacing = 3.sp, fontSize = 12.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (last != null && !last.displayLogoUrl.isNullOrBlank()) {
                        AsyncImage(model = last.displayLogoUrl, contentDescription = null, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(last?.name ?: stringResource(R.string.stayon_no_history), fontSize = 27.sp, color = White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (guide != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(guide.title, fontSize = 22.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(stringResource(R.string.stayon_tv_hint), fontSize = 17.sp, color = Muted)
                        }
                    }
                }
                if (guide != null) {
                    val progress = ((now - guide.startMs).toFloat() / (guide.stopMs - guide.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(.63f).height(5.dp).background(Color(0xFF444448), RoundedCornerShape(9.dp))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Yellow, RoundedCornerShape(9.dp)))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (last != null) StayButton(stringResource(R.string.stayon_watch), { onWatchChannel(last.id) })
                    StayTextAction(stringResource(R.string.stayon_browse_tv), onOpenTv)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                StayLineItem(stringResource(R.string.stayon_guide_icon), stringResource(R.string.stayon_guide), stringResource(R.string.stayon_guide_desc), onOpenGuide)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF444448)))
                Text(stringResource(R.string.stayon_favorite_title), fontSize = 12.sp, letterSpacing = 2.sp, color = Muted)
                if (fav != null) {
                    StayLineItem(stringResource(R.string.stayon_star_icon), fav.name, stringResource(R.string.stayon_open_channel), { onWatchChannel(fav.id) })
                } else {
                    Text(stringResource(R.string.stayon_add_favorites), color = White, fontSize = 17.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF444448)))
                Text(stringResource(R.string.stayon_next_match), fontSize = 12.sp, letterSpacing = 2.sp, color = Muted)
                Text(stringResource(R.string.stayon_set_team), fontSize = 17.sp, color = White)
                Text(stringResource(R.string.stayon_sports_pending), fontSize = 13.sp, color = Muted, maxLines = 2)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3B3B40)))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stayon_love_l), fontSize = 28.sp, fontWeight = FontWeight.Black, color = White)
                    Text(stringResource(R.string.stayon_love_o), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Yellow)
                    Text(stringResource(R.string.stayon_love_tail), fontSize = 28.sp, fontWeight = FontWeight.Black, color = White)
                }
                Text(stringResource(R.string.stayon_interactive_tv), fontSize = 12.sp, color = Muted)
            }
            StayServiceLabel(stringResource(R.string.stayon_mediaset), stringResource(R.string.stayon_telecinco), Modifier.weight(1f))
            StayServiceLabel(stringResource(R.string.stayon_rtve), stringResource(R.string.stayon_la1), Modifier.weight(1f))
            StayServiceLabel(stringResource(R.string.stayon_atresmedia), stringResource(R.string.stayon_antena3), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StayLineItem(icon: String, title: String, desc: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Panel else Color.Transparent)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 27.sp, color = if (focused) Yellow else White)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 20.sp, color = White, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun StayServiceLabel(name: String, subtitle: String, modifier: Modifier = Modifier) {
    // Deliberately not interactive until this provider's integration is approved.
    Column(modifier.padding(start = 12.dp)) {
        Text(name, fontSize = 21.sp, color = White, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, fontSize = 15.sp, color = Yellow)
    }
}

@Composable
private fun MusicDashboard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Panel, Color(0xFF181613), Color(0xFF292019))))
                .padding(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stayon_your_station), fontSize = 13.sp, letterSpacing = 3.sp, color = Muted)
                Text(stringResource(R.string.stayon_urban), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = White)
                Text(stringResource(R.string.stayon_music_pending), fontSize = 18.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.stayon_now_playing), fontSize = 12.sp, letterSpacing = 3.sp, color = Muted)
                Text(stringResource(R.string.stayon_radio_pending), fontSize = 18.sp, color = White, maxLines = 2)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.stayon_listen), fontSize = 17.sp, color = Muted)
            }
            Box(Modifier.weight(.8f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(218.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF6D5129), Color(0xFF171614), Color(0xFF101013))))
                        .border(3.dp, Color(0xFF7C6338), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.stayon_music_symbol), color = Yellow, fontSize = 113.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3B3B40)))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stayon_music_all), fontSize = 18.sp, color = White)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(stringResource(R.string.stayon_spotify), stringResource(R.string.stayon_youtube_music), stringResource(R.string.stayon_amazon_music)).forEach { title ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1C1C21)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = White)
                }
            }
        }
    }
}
