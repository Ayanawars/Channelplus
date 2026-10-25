package tv.own.owntv.features.setup

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.core.sync.TunerSourceSyncer
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon

/** Imports a read-only mirror of channels already tuned by the television. */
@Composable
internal fun TunerSourceOption(onImport: (name: String, url: String) -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.tuner_source_title)
    val description = stringResource(R.string.tuner_source_description)
    val noInputs = stringResource(R.string.tuner_source_missing)
    var inputs by remember { mutableStateOf<List<TunerSourceSyncer.Input>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val discover = {
        scope.launch {
            inputs = withContext(Dispatchers.IO) { runCatching { TunerSourceSyncer.discover(context) }.getOrDefault(emptyList()) }
            if (inputs.isEmpty()) Toast.makeText(context, noInputs, Toast.LENGTH_LONG).show()
            else choosing = true
        }
    }
    val withTunerPermission = rememberTunerPermission()
    StageSettingRow(
        icon = OwnTVIcon.LIVE_TV,
        title = title,
        desc = description,
        value = SettingValue.Opens(null),
        onClick = {
            withTunerPermission { discover() }
        },
        help = SettingHelp(title, description),
    )
    if (choosing) PickerDialog(
        title = title,
        options = inputs.map { it.id to it.name },
        selected = inputs.firstOrNull()?.id.orEmpty(),
        onSelect = { id ->
            choosing = false
            val input = inputs.firstOrNull { it.id == id }
            onImport(
                if (TunerSourceSyncer.isPluto(input?.providerPackage)) input?.name ?: title else title,
                TunerSourceSyncer.sourceUrl(id, input?.providerPackage, input?.filterByOwner == true),
            )
        },
        onDismiss = { choosing = false },
    )
}
