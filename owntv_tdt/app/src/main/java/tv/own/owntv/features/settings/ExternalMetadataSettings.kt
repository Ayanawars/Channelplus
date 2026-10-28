package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.metadata.ExternalMetadataConfig
import tv.own.owntv.core.metadata.ExternalMetadataRepository
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

@Composable
internal fun ExternalMetadataSettingRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val repository = koinInject<ExternalMetadataRepository>()
    val config by repository.store.config.collectAsStateWithLifecycle()
    StageSettingRow(icon = OwnTVIcon.INFO, title = stringResource(R.string.external_metadata_title),
        desc = stringResource(R.string.external_metadata_summary),
        value = SettingValue.Opens(stringResource(R.string.settings_key_open)), onClick = onClick, modifier = modifier,
        help = SettingHelp(stringResource(R.string.external_metadata_title), stringResource(R.string.external_metadata_description)))
}

@Composable
internal fun ExternalMetadataPopup(onDismiss: () -> Unit) {
    val repository = koinInject<ExternalMetadataRepository>()
    val config by repository.store.config.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(config.enabled) }
    var tvdb by remember { mutableStateOf(config.tvdbKey) }
    var pin by remember { mutableStateOf(config.tvdbPin) }
    var trakt by remember { mutableStateOf(config.traktClientId) }
    var busy by remember { mutableStateOf(false) }
    var failedSave by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExternalMetadataRepository.ServiceTest?>(null) }
    var tested by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    fun save(test: Boolean) {
        if (busy) return
        busy = true; failedSave = false; saved = false; result = null; tested = false
        scope.launch {
            try {
                repository.configure(ExternalMetadataConfig(enabled, tvdb.trim(), pin.trim(), trakt.trim()))
                saved = true
                if (test) { result = repository.test(); tested = true }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { failedSave = true }
            finally { busy = false }
        }
    }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.external_metadata_title), width = 960.mpx,
        buttons = {
            StageButton(stringResource(R.string.content_close), onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus), height = 56.mpx, textSize = 18)
            StageButton(stringResource(R.string.external_metadata_save), onClick = { save(false) }, height = 56.mpx, textSize = 18)
            StageButton(stringResource(R.string.external_metadata_save_test), onClick = { save(true) }, height = 56.mpx, textSize = 18)
        }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.mpx).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
            Text(stringResource(R.string.external_metadata_description), style = stageText(17, 400), color = StageColors.Muted)
            StageSettingRow(OwnTVIcon.INFO, stringResource(R.string.external_metadata_enabled), desc = null, value = SettingValue.Switch(enabled), onClick = { enabled = !enabled })
            StageFieldRow(OwnTVIcon.PENCIL, stringResource(R.string.external_trakt_client_id), trakt, { trakt = it }, password = true)
            StageFieldRow(OwnTVIcon.PENCIL, stringResource(R.string.external_tvdb_key), tvdb, { tvdb = it }, password = true)
            StageFieldRow(OwnTVIcon.PENCIL, stringResource(R.string.external_tvdb_pin), pin, { pin = it }, password = true)
            Text(stringResource(R.string.external_metadata_credentials_help), style = stageText(16, 400), color = StageColors.Muted)
            if (busy) Text(stringResource(R.string.external_metadata_testing), style = stageText(18, 500), color = StageColors.Text)
            if (failedSave) Text(stringResource(R.string.external_metadata_save_failed), style = stageText(18, 500), color = StageColors.Text)
            if (saved && !busy) Text(stringResource(R.string.external_metadata_saved), style = stageText(18, 500), color = StageColors.Text)
            if (tested) {
                val state = result
                fun status(ok: Boolean?) = when (ok) { true -> R.string.external_metadata_ok; false -> R.string.external_metadata_failed; null -> R.string.external_metadata_unconfigured }
                Text(stringResource(R.string.external_metadata_test_result, stringResource(status(state?.trakt)), stringResource(status(state?.tvdb))),
                    style = stageText(18, 500), color = StageColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
