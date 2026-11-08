package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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

/**
 * Sequential channel-to-EPG editing. The picker only stages the assignment; Save commits every
 * edit in one DataStore update. Search changes never discard edits to channels outside the query.
 */
@Composable
internal fun EpgChannelAssignment(onDismiss: () -> Unit) {
    val vm: EpgViewModel = koinViewModel()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ChannelEntity?>(null) }
    var nextFocusId by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    // Keep the channel entity: filtered-out channels must still be saved.
    // A null guide ID is an explicit staged "use automatic EPG" action.
    val staged = remember { mutableStateMapOf<Long, Pair<ChannelEntity, String?>>() }
    val searchFocus = remember { FocusRequester() }
    val rowFocus = remember { mutableMapOf<Long, FocusRequester>() }
    val listState = rememberLazyListState()
    val restoreFocus = rememberDialogFocusRestore(selected != null)
    val channels = key(query) {
        val result by produceState<List<Pair<ChannelEntity, String>>?>(null) {
            if (query.isNotBlank()) delay(250)
            value = vm.channelsForAssignment(query)
        }
        result
    }

    fun close() {
        if (saving) return
        if (staged.isEmpty()) onDismiss() else showDiscard = true
    }

    fun stage(channel: ChannelEntity, guideId: String?) {
        val normalized = guideId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val original = vm.currentEpgMatch(channel)
        if (original == normalized) staged.remove(channel.id)
        else staged[channel.id] = channel to normalized
        val index = channels.orEmpty().indexOfFirst { it.first.id == channel.id }
        nextFocusId = channels?.getOrNull(index + 1)?.first?.id ?: channel.id
        selected = null
    }

    LaunchedEffect(selected, nextFocusId, channels) {
        val id = nextFocusId ?: return@LaunchedEffect
        if (selected != null) return@LaunchedEffect
        val index = channels?.indexOfFirst { it.first.id == id } ?: -1
        if (index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { rowFocus[id]?.requestFocus() }
        }
        nextFocusId = null
    }

    StagePopup(
        onDismiss = ::close,
        title = stringResource(R.string.stayon_epg_assign_channel),
        body = stringResource(R.string.stayon_epg_batch_hint, staged.size),
        scroll = false,
        buttons = {
            StageButton(stringResource(R.string.content_close), onClick = ::close)
            if (staged.isNotEmpty()) StageButton(
                stringResource(R.string.common_save),
                tinted = true,
                onClick = {
                    if (!saving) {
                        saving = true
                        vm.saveManualEpgAssignments(staged.values.toMap()) { success ->
                            saving = false
                            if (success) {
                                staged.clear()
                                onDismiss()
                            } else saveFailed = true
                        }
                    }
                },
            )
        },
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
            else -> LazyColumn(Modifier.fillMaxWidth().height(380.mpx), state = listState) {
                items(channels, key = { it.first.id }) { (channel, sourceName) ->
                    val row = remember(channel.id) { FocusRequester() }
                    rowFocus[channel.id] = row
                    val change = staged[channel.id]
                    val manual = if (change != null) change.second else vm.currentEpgMatch(channel)
                    val label = when {
                        change != null -> stringResource(
                            R.string.stayon_epg_batch_pending,
                            manual ?: stringResource(R.string.stayon_epg_batch_auto),
                        )
                        manual != null -> stringResource(R.string.stayon_epg_batch_manual, manual)
                        else -> sourceName
                    }
                    StageMenuItem(
                        text = channel.name,
                        value = label,
                        icon = OwnTVIcon.LIVE_TV,
                        modifier = Modifier.focusRequester(row),
                        onClick = { if (!saving) { restoreFocus.value = row; selected = channel } },
                        enabled = !saving,
                    )
                }
            }
        }
    }

    selected?.let { channel ->
        EpgMatchDialog(
            channelName = channel.name,
            currentMatch = if (staged.containsKey(channel.id)) staged[channel.id]?.second else vm.currentEpgMatch(channel),
            loadChannels = { vm.availableEpgChannels(channel.name, it) },
            onPick = { stage(channel, it) },
            onClear = { stage(channel, null) },
            onDismiss = { selected = null },
        )
    }
    if (showDiscard) StagePopup(
        onDismiss = { showDiscard = false },
        title = stringResource(R.string.stayon_epg_batch_discard_title),
        body = stringResource(R.string.stayon_epg_batch_discard_body, staged.size),
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = { showDiscard = false })
            StageButton(stringResource(R.string.stayon_epg_batch_discard), tinted = true,
                onClick = { staged.clear(); showDiscard = false; onDismiss() })
        },
    ) {}
    if (saveFailed) StagePopup(
        onDismiss = { saveFailed = false },
        title = stringResource(R.string.stayon_epg_batch_error_title),
        body = stringResource(R.string.stayon_epg_batch_error_body),
        buttons = {
            StageButton(stringResource(R.string.content_close), onClick = { saveFailed = false })
        },
    ) {}
}
