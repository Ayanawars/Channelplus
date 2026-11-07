package tv.own.owntv.features.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon

private enum class RadioForm { MANUAL, PLAYLIST }

/** Independent radio manager, separate from the IPTV television source catalogue. */
@Composable
fun RadioIptvSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val radio = remember(context) { StayonRadioLibrary(context) }
    val playlists by radio.playlists.collectAsStateWithLifecycle()
    val stations by radio.stations.collectAsStateWithLifecycle()
    val playing by RadioAudioPlayer.playingStationId.collectAsStateWithLifecycle()
    val playerError by RadioAudioPlayer.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<RadioForm?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRemoveStation by remember { mutableStateOf<StayonRadioStation?>(null) }
    var pendingRemovePlaylist by remember { mutableStateOf<StayonRadioPlaylist?>(null) }

    fun openForm(newForm: RadioForm) {
        name = ""; url = ""; logo = ""
        status = null
        form = newForm
    }

    StageFullPage(
        parents = listOf(stringResource(R.string.stayon_settings_sources)),
        title = stringResource(R.string.stayon_radio_iptv_title),
        count = "",
        onBack = { if (form != null) form = null else onBack() },
        modifier = modifier,
        scroll = rememberScrollState(),
    ) {
        if (form != null) {
            StageSettingsHeading(stringResource(
                if (form == RadioForm.MANUAL) R.string.stayon_radio_new_station
                else R.string.stayon_radio_new_playlist), null, first = true)
            StageFieldRow(
                icon = OwnTVIcon.PENCIL, label = stringResource(R.string.stayon_radio_name),
                value = name, onValueChange = { name = it.trimStart() },
                placeholder = stringResource(R.string.stayon_radio_name_hint),
            )
            StageFieldRow(
                icon = OwnTVIcon.NETWORK, label = stringResource(R.string.stayon_radio_url),
                value = url, onValueChange = { url = it.trim() },
                placeholder = stringResource(R.string.stayon_radio_url_hint),
            )
            if (form == RadioForm.MANUAL) StageFieldRow(
                icon = OwnTVIcon.IMAGE, label = stringResource(R.string.stayon_radio_logo),
                value = logo, onValueChange = { logo = it.trim() },
                placeholder = stringResource(R.string.stayon_radio_logo_hint),
            )
            if (status != null) StageSettingsNote(status.orEmpty(), null)
            StageSettingRow(
                icon = OwnTVIcon.CHECK,
                title = stringResource(R.string.stayon_radio_save),
                desc = stringResource(R.string.stayon_radio_save_desc),
                value = SettingValue.Action(stringResource(R.string.stayon_radio_save)),
                enabled = !busy && name.isNotBlank() && StayonRadioLibrary.isStreamUrl(url),
                onClick = {
                    if (form == RadioForm.MANUAL) {
                        runCatching { radio.addStation(name, url, logo) }
                            .onSuccess { form = null; status = null }
                            .onFailure { status = it.localizedMessage }
                    } else {
                        busy = true
                        scope.launch {
                            runCatching { radio.addPlaylist(name, url) }
                                .onSuccess { form = null; status = null }
                                .onFailure { status = it.localizedMessage }
                            busy = false
                        }
                    }
                },
            )
            StageSettingRow(
                icon = OwnTVIcon.BACK, title = stringResource(R.string.common_cancel),
                desc = null, value = SettingValue.Opens(null),
                onClick = { form = null; status = null },
            )
        } else {
            StageSettingsHeading(stringResource(R.string.stayon_radio_manage), null, first = true)
            StageSettingRow(
                icon = OwnTVIcon.ADD, title = stringResource(R.string.stayon_radio_new_playlist),
                desc = stringResource(R.string.stayon_radio_new_playlist_desc),
                value = SettingValue.Opens(null), onClick = { openForm(RadioForm.PLAYLIST) },
            )
            StageSettingRow(
                icon = OwnTVIcon.ADD, title = stringResource(R.string.stayon_radio_new_station),
                desc = stringResource(R.string.stayon_radio_new_station_desc),
                value = SettingValue.Opens(null), onClick = { openForm(RadioForm.MANUAL) },
            )
            if (status != null) StageSettingsNote(status.orEmpty(), null)
            StageSettingsHeading(stringResource(R.string.stayon_radio_playlists), playlists.size)
            playlists.forEach { list ->
                StageSettingRow(
                    icon = OwnTVIcon.REFRESH,
                    title = list.name,
                    desc = list.url,
                    value = SettingValue.Action(stringResource(R.string.stayon_radio_refresh)),
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { radio.refreshPlaylist(list.id) }
                                .onSuccess { status = context.getString(R.string.stayon_radio_updated, it) }
                                .onFailure { status = it.localizedMessage }
                            busy = false
                        }
                    },
                    onLongClick = { pendingRemovePlaylist = list },
                )
            }
            StageSettingsHeading(stringResource(R.string.stayon_radio_stations), stations.size)
            if (stations.isEmpty()) StageSettingsNote(stringResource(R.string.stayon_radio_empty), null)
            stations.forEach { station ->
                val isCurrent = playing == station.id
                StageSettingRow(
                    icon = if (isCurrent) OwnTVIcon.PAUSE else OwnTVIcon.AUDIO,
                    title = station.name,
                    desc = station.url,
                    value = SettingValue.Action(stringResource(
                        if (isCurrent) R.string.stayon_radio_stop else R.string.stayon_radio_listen)),
                    onClick = {
                        if (isCurrent) RadioAudioPlayer.stop()
                        else RadioAudioPlayer.play(station)
                    },
                    onLongClick = { pendingRemoveStation = station },
                )
            }
            if (playerError != null) StageSettingsNote(playerError.orEmpty(), null)
        }
    }

    pendingRemoveStation?.let { station ->
        ConfirmDialog(
            title = stringResource(R.string.stayon_radio_delete_station, station.name),
            message = stringResource(R.string.stayon_radio_delete_confirm),
            onConfirm = {
                if (playing == station.id) RadioAudioPlayer.stop()
                radio.deleteStation(station.id)
                pendingRemoveStation = null
            },
            onDismiss = { pendingRemoveStation = null },
        )
    }
    pendingRemovePlaylist?.let { list ->
        ConfirmDialog(
            title = stringResource(R.string.stayon_radio_delete_playlist, list.name),
            message = stringResource(R.string.stayon_radio_delete_confirm),
            onConfirm = {
                if (stations.any { it.id == playing && it.playlistId == list.id }) RadioAudioPlayer.stop()
                radio.deletePlaylist(list.id)
                pendingRemovePlaylist = null
            },
            onDismiss = { pendingRemovePlaylist = null },
        )
    }
}
