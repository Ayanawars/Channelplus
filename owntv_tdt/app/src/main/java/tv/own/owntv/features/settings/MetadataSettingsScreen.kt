package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.text.style.TextOverflow

/**
 * TMDB content languages (ISO 639-1, region-qualified where TMDB's coverage is meaningfully better for
 * one — e.g. pt-BR). "" keeps TMDB's own default (en-US), which is what installs used before this setting
 * existed, so an upgrade never silently changes anyone's metadata.
 *
 * Distinct from VideoPlayerSettingsScreen's LANGUAGES list, which uses 3-letter codes for audio/subtitle
 * track matching — TMDB only accepts 2-letter tags.
 */
private val TMDB_LANGUAGE_CODES = listOf(
    "", MetadataConfig.LANGUAGE_AUTO, "ar", "bg", "zh", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "ms", "no", "fa", "pl", "pt-BR", "pt-PT", "ro", "ru", "sr", "sk", "sl", "es", "es-MX", "sv", "th", "tr", "uk", "vi",
)

@Composable
private fun tmdbLangName(code: String): String = stringResource(
    when (code) {
        "" -> R.string.settings_language_default
        MetadataConfig.LANGUAGE_AUTO -> R.string.settings_language_device
        "ar" -> R.string.settings_language_arabic
        "bg" -> R.string.settings_language_bulgarian
        "zh" -> R.string.settings_language_chinese
        "hr" -> R.string.settings_language_croatian
        "cs" -> R.string.settings_language_czech
        "da" -> R.string.settings_language_danish
        "nl" -> R.string.settings_language_dutch
        "en" -> R.string.settings_language_english
        "et" -> R.string.settings_language_estonian
        "fi" -> R.string.settings_language_finnish
        "fr" -> R.string.settings_language_french
        "de" -> R.string.settings_language_german
        "el" -> R.string.settings_language_greek
        "he" -> R.string.settings_language_hebrew
        "hi" -> R.string.settings_language_hindi
        "hu" -> R.string.settings_language_hungarian
        "id" -> R.string.settings_language_indonesian
        "it" -> R.string.settings_language_italian
        "ja" -> R.string.settings_language_japanese
        "ko" -> R.string.settings_language_korean
        "lv" -> R.string.settings_language_latvian
        "lt" -> R.string.settings_language_lithuanian
        "ms" -> R.string.settings_language_malay
        "no" -> R.string.settings_language_norwegian
        "fa" -> R.string.settings_language_persian
        "pl" -> R.string.settings_language_polish
        "pt-BR" -> R.string.settings_language_portuguese_brazil
        "pt-PT" -> R.string.settings_language_portuguese_portugal
        "ro" -> R.string.settings_language_romanian
        "ru" -> R.string.settings_language_russian
        "sr" -> R.string.settings_language_serbian
        "sk" -> R.string.settings_language_slovak
        "sl" -> R.string.settings_language_slovenian
        "es" -> R.string.settings_language_spanish
        "es-MX" -> R.string.settings_language_spanish_latam
        "sv" -> R.string.settings_language_swedish
        "th" -> R.string.settings_language_thai
        "tr" -> R.string.settings_language_turkish
        "uk" -> R.string.settings_language_ukrainian
        "vi" -> R.string.settings_language_vietnamese
        else -> R.string.settings_language_default
    },
)

/**
 * Settings → Metadata (TMDB): the master toggle and the two advanced
 * access tiers (own TMDB key / self-host URL), plus a manual "look up title" test that proves the
 * configured tier reaches TMDB end-to-end. Enrichment of actual detail screens arrives in later phases.
 *
 * Precedence: self-host URL > own key > the default caching Worker (zero setup).
 */
private fun metadataModeLabelRes(mode: tv.own.owntv.core.metadata.MetadataMode): Int = when (mode) {
    tv.own.owntv.core.metadata.MetadataMode.PROVIDER -> R.string.settings_metadata_provider_only
    tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB -> R.string.settings_metadata_provider_plus_tmdb
    tv.own.owntv.core.metadata.MetadataMode.TMDB_ONLY -> R.string.settings_metadata_tmdb_only
}

private fun metadataTierLabelRes(tier: tv.own.owntv.core.metadata.MetadataConfig.Tier): Int = when (tier) {
    tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER -> R.string.settings_tier_default
    tv.own.owntv.core.metadata.MetadataConfig.Tier.OWN_KEY -> R.string.settings_tier_key
    tv.own.owntv.core.metadata.MetadataConfig.Tier.SELF_HOST -> R.string.settings_tier_self_host
}

@Composable
fun MetadataSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val language by vm.metadataLanguage.collectAsStateWithLifecycle()
    var showMode by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showServices by remember { mutableStateOf(false) }
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(showMode, showLanguage, showServices) {
        if (!showMode && !showLanguage && !showServices) runCatching { rowsFocus.requestFocus() }
    }
    StageFullPage(parents = listOf(stringResource(R.string.settings_group_content_metadata)),
        title = stringResource(R.string.settings_metadata), count = pluralStringResource(R.plurals.settings_setting_count, 3, 3), onBack = onBack, modifier = modifier, rowsFocus = rowsFocus) {
        StageSettingRow(icon = OwnTVIcon.MOVIES, title = stringResource(R.string.settings_metadata_source),
            desc = null, value = SettingValue.Choice(stringResource(metadataModeLabelRes(mode))), onClick = { showMode = true },
            modifier = Modifier.focusRequester(rowsFocus))
        StageSettingRow(icon = OwnTVIcon.LANGUAGE, title = stringResource(R.string.settings_metadata_language),
            desc = null, value = SettingValue.Choice(tmdbLangName(language)), onClick = { showLanguage = true })
        ExternalMetadataSettingRow(onClick = { showServices = true })
        Text(stringResource(R.string.external_metadata_description), style = stageText(17, 400),
            color = StageColors.Muted, modifier = Modifier.padding(22.mpx))
    }
    if (showServices) ExternalMetadataPopup(onDismiss = { showServices = false })
    if (showMode) PickerDialog(title = stringResource(R.string.settings_metadata_source),
        options = tv.own.owntv.core.metadata.MetadataMode.entries.map { it.name to stringResource(metadataModeLabelRes(it)) },
        selected = mode.name, onSelect = { picked ->
            tv.own.owntv.core.metadata.MetadataMode.entries.firstOrNull { it.name == picked }?.let(vm::setMetadataMode)
            showMode = false
        }, onDismiss = { showMode = false })
    if (showLanguage) PickerDialog(title = stringResource(R.string.settings_metadata_language),
        options = TMDB_LANGUAGE_CODES.map { it to tmdbLangName(it) }, selected = language,
        onSelect = { vm.setMetadataLanguage(it); showLanguage = false }, onDismiss = { showLanguage = false })
}

@Composable
internal fun CompanionKeyDialog(
    titleRes: Int,
    state: tv.own.owntv.core.companion.CompanionServerState,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        onStart(tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT)
        kotlinx.coroutines.delay(60)
        runCatching { closeFocus.requestFocus() }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onStop() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(titleRes),
        width = 820.mpx,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(closeFocus)) },
    ) { tv.own.owntv.ui.components.StageCompanionStatus(state) }
}
