package tv.own.owntv.features.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx

private data class InstalledTvApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val tvOptimized: Boolean,
)

/**
 * Shows installed launcher apps, not all installed packages. LauncherApps works with the
 * system's package-visibility policy and doesn't require QUERY_ALL_PACKAGES.
 *
 * This page is a real, standalone local application manager (open, favorite, hide,
 * reveal and system details). It does not pretend to provide music-provider APIs.
 */
@Composable
fun InstalledAppsSettingsRows() {
    val context = LocalContext.current
    val launcher = remember(context) { context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps }
    val prefs = remember(context) { context.getSharedPreferences("stayon_apps_settings", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledTvApp>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var showNonTv by remember { mutableStateOf(prefs.getBoolean("show_non_tv", false)) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden", emptySet()).orEmpty().toSet()) }
    var selected by remember { mutableStateOf<InstalledTvApp?>(null) }

    // Update the list if the user installs/uninstalls an app while Settings is open.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(refresh) {
        busy = true
        apps = withContext(Dispatchers.IO) {
            runCatching {
                val me = Process.myUserHandle()
                launcher.getActivityList(null, me).map { info ->
                    InstalledTvApp(
                        label = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName },
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = info.user,
                        tvOptimized = context.packageManager.getLeanbackLaunchIntentForPackage(info.componentName.packageName) != null,
                    )
                }.distinctBy { it.packageName }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }.getOrElse { emptyList() }
        }
        busy = false
    }

    fun updateFavorites(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
    fun updateHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    val visible = apps.filter { (showNonTv || it.tvOptimized) && (showHidden || it.packageName !in hidden) }
        .sortedWith(compareBy<InstalledTvApp> { if (it.packageName in favorites) 0 else 1 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    StageSettingsHeading(stringResource(R.string.stayon_apps_options), null, first = true)
    StageSettingRow(OwnTVIcon.LIST, stringResource(R.string.stayon_apps_show_non_tv),
        stringResource(R.string.stayon_apps_show_non_tv_desc), SettingValue.Switch(showNonTv),
        onClick = {
            showNonTv = !showNonTv
            prefs.edit().putBoolean("show_non_tv", showNonTv).apply()
        })
    StageSettingRow(OwnTVIcon.EYE_OFF, stringResource(R.string.stayon_apps_show_hidden),
        stringResource(R.string.stayon_apps_show_hidden_desc), SettingValue.Switch(showHidden),
        onClick = {
            showHidden = !showHidden
            prefs.edit().putBoolean("show_hidden", showHidden).apply()
        })
    StageSettingRow(OwnTVIcon.REFRESH, stringResource(R.string.stayon_apps_refresh),
        null, SettingValue.Action(stringResource(R.string.stayon_apps_refresh)), onClick = { refresh++ })

    StageSettingsHeading(stringResource(R.string.stayon_apps_installed), visible.size)
    if (busy) StageSettingsNote(stringResource(R.string.stayon_apps_scanning), null)
    else if (visible.isEmpty()) StageSettingsNote(stringResource(R.string.stayon_apps_empty), null)
    visible.forEach { app ->
        StageSettingRow(
            icon = if (app.packageName in favorites) OwnTVIcon.STAR else OwnTVIcon.GRID,
            title = app.label,
            desc = app.packageName,
            value = SettingValue.Opens(if (app.packageName in favorites) stringResource(R.string.stayon_apps_favorite) else null),
            onClick = { selected = app },
        )
    }

    selected?.let { app ->
        BackHandler { selected = null }
        val first = remember(app.packageName) { FocusRequester() }
        LaunchedEffect(app.packageName) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        OwnTVPopup(onDismissRequest = { selected = null }, stageLayout = true) {
            Box(Modifier.fillMaxSize().background(StageColors.Scrim), contentAlignment = Alignment.Center) {
                StageMenu(Modifier.width(530.mpx).trapAllFocusExit().focusGroup()) {
                    StageMenuHeader(app.label, app.packageName)
                    StageMenuItem(stringResource(R.string.stayon_apps_open),
                        onClick = {
                            runCatching { launcher.startMainActivity(app.component, app.user, null, null) }
                            selected = null
                        },
                        icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(first))
                    StageMenuItem(stringResource(if (app.packageName in favorites)
                        R.string.stayon_apps_unfavorite else R.string.stayon_apps_add_favorite),
                        onClick = { updateFavorites(app.packageName); selected = null },
                        icon = OwnTVIcon.STAR)
                    StageMenuItem(stringResource(if (app.packageName in hidden)
                        R.string.stayon_apps_unhide else R.string.stayon_apps_hide),
                        onClick = { updateHidden(app.packageName); selected = null },
                        icon = OwnTVIcon.EYE_OFF)
                    StageMenuItem(stringResource(R.string.stayon_apps_system_info),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                            selected = null
                        },
                        icon = OwnTVIcon.INFO)
                }
            }
        }
    }
}
