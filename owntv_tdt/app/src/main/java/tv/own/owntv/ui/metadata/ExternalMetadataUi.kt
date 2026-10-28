package tv.own.owntv.ui.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import tv.own.owntv.ui.theme.hudAccent
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

private val artworkCache = ProgrammeArtworkCache<List<Any?>>()

/** Cached values are scoped by programme, provider configuration and profile. */
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
    val identity = listOf(title, details?.year, details?.categories, details?.episode, tmdbId, type, config, metadata, profile)
    // key() gives produceState fresh storage immediately, not one recomposition after a channel change.
    return key(identity) {
        val result by produceState(artworkCache.get(identity)) {
            if (!config.enabled) return@produceState
            if (value == null) delay(MetadataRepository.FOCUS_DEBOUNCE_MS)
            val publish: (ExternalTitleMetadata) -> Unit = { data ->
                artworkCache.put(identity, data)
                value = data
            }
            val resolved = try {
                if (tmdbId != null && type != null) repository.known(tmdbId, type, publish)
                else title?.takeIf { it.isNotBlank() }?.let { repository.live(it, details, publish) }
            } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
            resolved?.let(publish)
        }
        result
    }
}

@Composable
fun ExternalMetadataBadges(data: ExternalTitleMetadata?, modifier: Modifier = Modifier, color: Color = Color.White,
    details: EpgDetails? = null) {
    val age = SpanishProgrammeBadges.age(details?.rating) ?: SpanishProgrammeBadges.age(data?.ageRating)
    val descriptors = SpanishProgrammeBadges.descriptors(details?.categoryList.orEmpty() + listOfNotNull(details?.rating))
    val rating = data?.imdbRating
    if (rating == null && age == null && descriptors.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rating != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage("file:///android_asset/programme-badges/imdb.svg", contentDescription = stringResource(R.string.programme_imdb),
                    contentScale = ContentScale.Fit, modifier = Modifier.width(56.dp).height(28.dp))
                Text(stringResource(R.string.programme_score, rating), style = stageText(22, 700), color = hudAccent, maxLines = 1)
            }
        }
        if (age != null) {
            if (rating != null) BadgeSeparator(color)
            AsyncImage("file:///android_asset/programme-badges/age-$age.svg", contentDescription = stringResource(R.string.programme_age, age.uppercase()),
                contentScale = ContentScale.Fit, modifier = Modifier.width(28.dp).height(28.dp))
        }
        descriptors.forEachIndexed { index, descriptor ->
            if (rating != null || age != null || index > 0) BadgeSeparator(color)
            AsyncImage("file:///android_asset/programme-badges/$descriptor.svg", contentDescription = stringResource(when (descriptor) {
                "violencia" -> R.string.programme_violence
                "lenguaje" -> R.string.programme_language
                "sexo" -> R.string.programme_sex
                "miedo-o-angustia" -> R.string.programme_fear
                "drogas-y-sustancias-toxicas" -> R.string.programme_drugs
                "discriminacion" -> R.string.programme_discrimination
                else -> R.string.programme_imitable
            }), contentScale = ContentScale.Fit, modifier = Modifier.width(28.dp).height(28.dp))
        }
    }
}

@Composable
private fun BadgeSeparator(color: Color) = Text(stringResource(R.string.programme_bullet), style = stageText(20, 500), color = color)
