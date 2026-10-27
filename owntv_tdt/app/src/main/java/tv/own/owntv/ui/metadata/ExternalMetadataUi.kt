package tv.own.owntv.ui.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Disposing or changing the programme cancels its lookup; the new title never inherits old badges. */
@Composable
fun rememberExternalMetadata(
    title: String?, details: EpgDetails? = null, tmdbId: Int? = null, type: MetadataType? = null,
): ExternalTitleMetadata? {
    val repository = koinInject<ExternalMetadataRepository>()
    val settings = koinInject<SettingsRepository>()
    val config by repository.store.config.collectAsStateWithLifecycle()
    val metadata by settings.metadataConfigFlow.collectAsStateWithLifecycle(initialValue = MetadataConfig())
    // The profile participates in the effect key so a kid-profile change cancels stale requests.
    val profile by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = 0L)
    val identity = listOf(title, details, tmdbId, type, config, metadata, profile)
    // key() gives produceState fresh storage immediately, not one recomposition after a channel change.
    return key(identity) {
        val result by produceState<ExternalTitleMetadata?>(null) {
            if (!metadata.enabled || !config.enabled) return@produceState
            delay(MetadataRepository.FOCUS_DEBOUNCE_MS)
            value = try {
                if (tmdbId != null && type != null) repository.known(tmdbId, type)
                else title?.takeIf { it.isNotBlank() }?.let { repository.live(it, details) }
            } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
        }
        result
    }
}

@Composable
fun ExternalMetadataBadges(data: ExternalTitleMetadata?, modifier: Modifier = Modifier, color: Color = Color.White) {
    data ?: return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
        data.tmdbRating?.let { Text(stringResource(R.string.external_tmdb_rating, it), style = stageText(18, 500), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (data.tvdbLoaded) Text(stringResource(R.string.external_tvdb_source), style = stageText(18, 500), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
