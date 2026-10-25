package tv.own.owntv.features.setup

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val noPermission = stringResource(R.string.tuner_source_permission)
    val noInputs = stringResource(R.string.tuner_source_missing)
    var inputs by remember { mutableStateOf<List<TunerSourceSyncer.Input>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    val discover = {
        inputs = runCatching { TunerSourceSyncer.discover(context) }.getOrDefault(emptyList())
        if (inputs.isEmpty()) Toast.makeText(context, noInputs, Toast.LENGTH_LONG).show()
        else choosing = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) discover() else Toast.makeText(context, noPermission, Toast.LENGTH_LONG).show()
    }
    StageSettingRow(
        icon = OwnTVIcon.LIVE_TV,
        title = title,
        desc = description,
        value = SettingValue.Opens(null),
        onClick = {
            if (context.checkSelfPermission(TunerSourceSyncer.READ_PERMISSION) == PackageManager.PERMISSION_GRANTED) discover()
            else permission.launch(TunerSourceSyncer.READ_PERMISSION)
        },
        help = SettingHelp(title, description),
    )
    if (choosing) PickerDialog(
        title = title,
        options = inputs.map { it.id to it.name },
        selected = inputs.firstOrNull()?.id.orEmpty(),
        onSelect = { id -> choosing = false; onImport(title, TunerSourceSyncer.sourceUrl(id)) },
        onDismiss = { choosing = false },
    )
}
