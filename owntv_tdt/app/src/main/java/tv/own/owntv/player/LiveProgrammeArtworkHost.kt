package tv.own.owntv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.metadata.ExternalTitleMetadata
import tv.own.owntv.ui.metadata.rememberExternalMetadata

internal data class ProgrammeArtworkIdentity(val title: String?, val startMs: Long?, val stopMs: Long?)
internal fun programmeArtworkIdentity(epg: EpgNowNext?) =
    ProgrammeArtworkIdentity(epg?.now?.title, epg?.now?.startMs, epg?.now?.stopMs)

internal data class LiveProgrammeArtwork(val identity: ProgrammeArtworkIdentity, val metadata: ExternalTitleMetadata?)
internal val LocalLiveProgrammeArtwork = staticCompositionLocalOf<LiveProgrammeArtwork?> { null }

/** Owns enrichment for the playing programme, including while the HUD is hidden. */
@Composable
internal fun LiveProgrammeArtworkHost(
    live: StateFlow<EpgNowNext?>,
    archive: StateFlow<EpgNowNext?>,
    watching: StateFlow<Long?>,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) { content(); return }
    val liveGuide by live.collectAsStateWithLifecycle()
    val archiveGuide by archive.collectAsStateWithLifecycle()
    // Position ticks must not recompose the shell or restart the artwork lookup every second.
    val archiveMode = remember(watching) { watching.map { it != null }.distinctUntilChanged() }
    val rewound by archiveMode.collectAsStateWithLifecycle(initialValue = watching.value != null)
    val guide = if (rewound) archiveGuide else liveGuide
    val metadata = rememberExternalMetadata(guide?.now?.title, guide?.nowDetails)
    PrefetchProgrammeImage(metadata?.posterUrl ?: metadata?.tvdbArtwork, 320, 480)
    PrefetchProgrammeImage(metadata?.logoUrl, 640, 320)
    CompositionLocalProvider(LocalLiveProgrammeArtwork provides LiveProgrammeArtwork(programmeArtworkIdentity(guide), metadata)) {
        content()
    }
}

@Composable
private fun PrefetchProgrammeImage(url: String?, width: Int, height: Int) {
    val context = LocalContext.current
    LaunchedEffect(url, context) {
        if (url != null) SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(url).size(width, height).crossfade(false).build(),
        )
    }
}
