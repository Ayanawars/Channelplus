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
private val Panel = Color(0xFF17181D)
private val Muted = Color(0xFFA7A7AD)
private val White = Color(0xFFFFF8EB)
private val Yellow = Color(0xFFFFDF32)

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
                StayTab.TV to stringResource(R.string.stayon_tv), StayTab.MUSIC to stringResource(R.string.stayon_music),
                StayTab.MOVIES to stringResource(R.string.stayon_movies), StayTab.SERIES to stringResource(R.string.stayon_series),
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
                Modifier.weight(2.17f).fillMaxHeight()
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
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(top = 8.dp, bottom = 12.dp)
                    .border(width = 0.dp, color = Color.Transparent)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                StayLineItem(stringResource(R.string.stayon_guide_icon), stringResource(R.string.stayon_guide), stringResource(R.string.stayon_guide_desc), onOpenGuide)
                StayDivider()
                Text(stringResource(R.string.stayon_favorite_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = White)
                if (favorite != null) {
                    StayLineItem(stringResource(R.string.stayon_star_icon), favorite.name, stringResource(R.string.stayon_open_channel), { onWatchChannel(favorite.id) }, favorite.displayLogoUrl)
                } else {
                    Text(stringResource(R.string.stayon_add_favorites), color = Muted, fontSize = 16.sp)
                }
                StayDivider()
                Text(stringResource(R.string.stayon_next_match), fontSize = 12.sp, letterSpacing = 2.sp, color = Muted)
                if (match != null) {
                    val home = match.home?.team
                    val away = match.away?.team
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (home?.logoUrl != null) {
                            AsyncImage(model = home.logoUrl, contentDescription = home.name, contentScale = ContentScale.Fit, modifier = Modifier.size(54.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.stayon_vs), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        if (away?.logoUrl != null) {
                            AsyncImage(model = away.logoUrl, contentDescription = away.name, contentScale = ContentScale.Fit, modifier = Modifier.size(54.dp))
                        }
                    }
                    StayTextAction(match.title, onClick = { onOpenMatch(match) })
                    val dateFormat = stringResource(R.string.stayon_match_date_format)
                    val time = remember(match.startMs, dateFormat) { SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date(match.startMs)) }
                    Text(time, fontSize = 14.sp, color = Muted)
                } else {
                    Text(stringResource(R.string.stayon_no_match), fontSize = 16.sp, color = Muted, maxLines = 2)
                }
            }
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

@Composable
private fun StayLineItem(icon: String, title: String, desc: String, onClick: () -> Unit, logoUrl: String? = null) {
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
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(42.dp))
        } else {
            Text(icon, fontSize = 27.sp, color = if (focused) Yellow else White)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 20.sp, color = White, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun MusicDashboard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Ink)) {
            Image(
                painterResource(R.drawable.stayon_music_hero), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = Alignment.CenterRight,
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
