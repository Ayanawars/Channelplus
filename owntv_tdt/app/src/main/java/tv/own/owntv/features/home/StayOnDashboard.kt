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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.core.epg.displayLogoUrl
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
                Text("StayON", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
                Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Yellow)
            }
            Spacer(Modifier.width(38.dp))
            val tabs = listOf(
                StayTab.TV to "TV", StayTab.MUSIC to "Música",
                StayTab.MOVIES to "Películas", StayTab.SERIES to "Series", StayTab.APPS to "Apps",
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
            StayTextAction("⌕", onOpenSearch)
            Spacer(Modifier.width(10.dp))
            StayTextAction("⚙", onOpenSettings)
            Spacer(Modifier.width(16.dp))
            val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
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
                Text("Próximamente en StayON+", fontSize = 27.sp, color = Muted)
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
                Text("ÚLTIMO CANAL VISTO", letterSpacing = 3.sp, fontSize = 12.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (last != null && !last.displayLogoUrl.isNullOrBlank()) {
                        AsyncImage(model = last.displayLogoUrl, contentDescription = null, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(last?.name ?: "Todavía no has visto ningún canal", fontSize = 27.sp, color = White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (guide != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(guide.title, fontSize = 22.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("Televisión TDT e IPTV", fontSize = 17.sp, color = Muted)
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
                    if (last != null) StayButton("▶  Continuar viendo", { onWatchChannel(last.id) })
                    StayTextAction("Ver canales  →", onOpenTv)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                StayLineItem("▦", "Guía TV", "Explora la programación", onOpenGuide)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF444448)))
                Text("TU CANAL FAVORITO", fontSize = 12.sp, letterSpacing = 2.sp, color = Muted)
                if (fav != null) {
                    StayLineItem("★", fav.name, "Abrir canal", { onWatchChannel(fav.id) })
                } else {
                    Text("Añade canales a favoritos", color = White, fontSize = 17.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF444448)))
                Text("PRÓXIMO PARTIDO", fontSize = 12.sp, letterSpacing = 2.sp, color = Muted)
                Text("Configura tu equipo favorito", fontSize = 17.sp, color = White)
                Text("Los partidos y canales se mostrarán al conectar el servicio.", fontSize = 13.sp, color = Muted, maxLines = 2)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3B3B40)))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", fontSize = 28.sp, fontWeight = FontWeight.Black, color = White)
                    Text("O", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Yellow)
                    Text("VEStv", fontSize = 28.sp, fontWeight = FontWeight.Black, color = White)
                }
                Text("Televisión interactiva", fontSize = 12.sp, color = Muted)
            }
            StayServiceLabel("Mediaset", "Telecinco", Modifier.weight(1f))
            StayServiceLabel("RTVE", "La 1", Modifier.weight(1f))
            StayServiceLabel("Atresmedia", "Antena 3", Modifier.weight(1f))
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
                Text("TU EMISORA TDT", fontSize = 13.sp, letterSpacing = 3.sp, color = Muted)
                Text("40 Urban", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = White)
                Text("Emisora de referencia · conexión pendiente", fontSize = 18.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Text("SONANDO AHORA", fontSize = 12.sp, letterSpacing = 3.sp, color = Muted)
                Text("Información de la emisora al conectar Radio TDT", fontSize = 18.sp, color = White, maxLines = 2)
                Spacer(Modifier.height(16.dp))
                Text("▷  Continuar escuchando", fontSize = 17.sp, color = Muted)
            }
            Box(Modifier.weight(.8f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(218.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF6D5129), Color(0xFF171614), Color(0xFF101013))))
                        .border(3.dp, Color(0xFF7C6338), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♫", color = Yellow, fontSize = 113.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3B3B40)))
        Spacer(Modifier.height(12.dp))
        Text("Tu música, en un solo lugar", fontSize = 18.sp, color = White)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Spotify", "YouTube Music", "Amazon Music").forEach { title ->
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
