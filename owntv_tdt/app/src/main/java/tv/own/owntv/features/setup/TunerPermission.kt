package tv.own.owntv.features.setup

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.sync.TunerSourceSyncer
import tv.own.owntv.ui.stage.StageConfirm

/** Run a user-requested tuner action only after Android authorizes listings access. */
@Composable
internal fun rememberTunerPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val unavailable = stringResource(R.string.tdt_permission_settings_unavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else showSettings = true
    }
    if (showSettings) StageConfirm(
        title = stringResource(R.string.tdt_permission_title),
        body = stringResource(R.string.tdt_permission_body),
        confirm = stringResource(R.string.tdt_permission_settings),
        cancel = stringResource(R.string.common_cancel),
        onConfirm = {
            showSettings = false
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
            }.onFailure { Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
        },
        onCancel = { showSettings = false },
    )
    return { action ->
        if (context.checkSelfPermission(TunerSourceSyncer.READ_PERMISSION) == PackageManager.PERMISSION_GRANTED) action()
        else if (pending == null) {
            pending = action
            launcher.launch(TunerSourceSyncer.READ_PERMISSION)
        }
    }
}
