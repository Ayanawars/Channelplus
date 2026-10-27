package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R

@Composable
internal fun LiveHudSizePicker(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val current by vm.liveHudSizePercent.collectAsStateWithLifecycle()
    PickerDialog(title = stringResource(R.string.live_hud_size_title),
        subtitle = stringResource(R.string.live_hud_size_description),
        options = (50..120 step 5).map { it.toString() to stringResource(R.string.common_percent, it) },
        selected = current.toString(),
        onSelect = { it.toIntOrNull()?.let(vm::setLiveHudSizePercent); onDismiss() },
        onDismiss = onDismiss)
}
