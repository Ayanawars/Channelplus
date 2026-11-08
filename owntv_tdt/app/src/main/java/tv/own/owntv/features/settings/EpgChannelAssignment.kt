package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.epg.EpgViewModel
import tv.own.owntv.features.live.EpgMatchDialog
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.rememberDialogFocusRestore
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.theme.mpx

@Composable
internal fun EpgChannelAssignment(onDismiss: () -> Unit) {
    val vm: EpgViewModel = koinViewModel()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ChannelEntity?>(null) }
    val searchFocus = remember { FocusRequester() }
    val restoreFocus = rememberDialogFocusRestore(selected != null)
    val channels = key(query) {
        val result by produceState<List<Pair<ChannelEntity, String>>?>(null) {
            if (query.isNotBlank()) delay(250)
            value = vm.channelsForAssignment(query)
        }
        result
    }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.stayon_epg_assign_channel),
        scroll = false,
        buttons = { StageButton(stringResource(R.string.content_close), onClick = onDismiss) },
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
        StageSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.content_search_channels),
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
        )
        when {
            channels == null -> OwnTVSpinner(sizeDp = 24)
            channels.isEmpty() -> StageSettingsNote(stringResource(R.string.stayon_epg_assign_empty), null)
            else -> LazyColumn(Modifier.fillMaxWidth().height(380.mpx)) {
                items(channels, key = { it.first.id }) { (channel, sourceName) ->
                    val row = remember { FocusRequester() }
                    StageMenuItem(
                        text = channel.name,
                        value = sourceName,
                        icon = OwnTVIcon.LIVE_TV,
                        modifier = Modifier.focusRequester(row),
                        onClick = { restoreFocus.value = row; selected = channel },
                    )
                }
            }
        }
    }

    selected?.let { channel ->
        EpgMatchDialog(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            loadChannels = { vm.availableEpgChannels(channel.name, it) },
            onPick = { vm.setEpgMatch(channel, it); selected = null },
            onClear = { vm.setEpgMatch(channel, null); selected = null },
            onDismiss = { selected = null },
        )
    }
}
