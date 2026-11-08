package tv.own.owntv.features.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.StayonHomeActivity
import tv.own.owntv.ui.components.OwnTVIcon

@Composable
internal fun StayonLauncherRows() {
    val context = LocalContext.current
    val manager = context.packageManager
    val component = remember(context) { ComponentName(context, StayonHomeActivity::class.java) }
    var offered by remember(component) {
        mutableStateOf(manager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }
    var confirmDisable by remember { mutableStateOf(false) }
    fun offer(value: Boolean) {
        runCatching {
            manager.setComponentEnabledSetting(component,
                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
            offered = value
        }.onFailure { Toast.makeText(context, R.string.stayon_launcher_unavailable, Toast.LENGTH_LONG).show() }
    }
    StageSettingRow(OwnTVIcon.HOME, stringResource(R.string.stayon_launcher_offer),
        stringResource(R.string.stayon_launcher_offer_desc), SettingValue.Switch(offered),
        onClick = { if (offered) confirmDisable = true else offer(true) })
    StageSettingRow(OwnTVIcon.SETTINGS, stringResource(R.string.stayon_launcher_system), null,
        SettingValue.Opens(null), onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Toast.makeText(context, R.string.stayon_launcher_unavailable, Toast.LENGTH_LONG).show() }
        })
    if (confirmDisable) ConfirmDialog(
        title = stringResource(R.string.stayon_launcher_disable),
        message = stringResource(R.string.stayon_launcher_disable_desc),
        onConfirm = { confirmDisable = false; offer(false) },
        onDismiss = { confirmDisable = false },
        confirmLabel = R.string.common_ok,
    )
}

internal enum class StayonMusicApp(val packageName: String, val label: Int, val logo: Int) {
    SPOTIFY("com.spotify.tv.android", R.string.stayon_spotify, R.drawable.stayon_spotify),
    YOUTUBE("com.google.android.youtube.tvmusic", R.string.stayon_youtube_music, R.drawable.stayon_youtube_music),
    AMAZON("com.amazon.music.tv", R.string.stayon_amazon_music, R.drawable.stayon_amazon_music);

    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)

    fun open(context: Context) {
        runCatching {
            context.startActivity(checkNotNull(launchIntent(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, R.string.stayon_music_app_unavailable, Toast.LENGTH_LONG).show() }
    }
}

@Composable
internal fun StayonMusicRows() {
    val context = LocalContext.current
    StageSettingsNote(stringResource(R.string.stayon_music_all), stringResource(R.string.stayon_music_app_desc))
    StayonMusicApp.entries.forEach { service ->
        val available = service.launchIntent(context) != null
        StageSettingRow(OwnTVIcon.AUDIO, stringResource(service.label), null,
            SettingValue.Opens(stringResource(if (available) R.string.stayon_apps_open else R.string.stayon_music_app_unavailable)),
            enabled = available, onClick = { service.open(context) })
    }
}
