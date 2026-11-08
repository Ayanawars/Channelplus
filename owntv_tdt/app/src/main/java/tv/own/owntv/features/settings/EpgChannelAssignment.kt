package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
internal fun EpgChannelAssignment(
    onDismiss: () -> Unit,
    initialChannel: ChannelEntity? = null,
) {
    val vm: EpgViewModel = koinViewModel()
    // Opening from a channel long-press starts with that channel, even in very large playlists.
    // Clear Search to continue editing the whole TV lineup in a single batch.
    var query by remember(initialChannel?.id) { mutableStateOf(initialChannel?.name.orEmpty()) }
    var filter by remember { mutableStateOf(EpgBatchFilter.ALL) }
    var initialFocusPending by remember(initialChannel?.id) { mutableStateOf(initialChannel != null) }
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
    val fetchedChannels = key(query) {
        val result by produceState<List<Pair<ChannelEntity, String>>?>(null) {
            if (query.isNotBlank()) delay(250)
            value = vm.channelsForAssignment(query)
        }
        result
    }
    // These filters affect the viewport only; every staged assignment survives search/filter changes.
    val channels = fetchedChannels?.filter { (channel, _) ->
        val queued = staged[channel.id]
        val manual = if (staged.containsKey(channel.id)) queued?.second else vm.currentEpgMatch(channel)
        when (filter) {
            EpgBatchFilter.ALL -> true
            EpgBatchFilter.UNASSIGNED -> manual == null && channel.epgChannelId.isNullOrBlank()
            EpgBatchFilter.MAPPED -> manual != null || !channel.epgChannelId.isNullOrBlank()
            EpgBatchFilter.PENDING -> staged.containsKey(channel.id)
        }
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
            // No channel requested: focus search. With a requested channel, focus its row below.
            if (initialChannel == null) runCatching { searchFocus.requestFocus() }
        }
        LaunchedEffect(channels, initialChannel?.id) {
            if (!initialFocusPending || channels == null || selected != null) return@LaunchedEffect
            val id = initialChannel?.id ?: return@LaunchedEffect
            val index = channels.indexOfFirst { it.first.id == id }
            if (index >= 0) {
                listState.scrollToItem(index)
                withFrameNanos { }
                withFrameNanos { }
                if (rowFocus[id] != null) runCatching { rowFocus[id]?.requestFocus() }
                else runCatching { searchFocus.requestFocus() }
            } else {
                runCatching { searchFocus.requestFocus() }
            }
            // Otherwise the first row would steal focus again after every staged change,
            // making it impossible to edit the next channel with the remote.
            initialFocusPending = false
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
            listOf(
                EpgBatchFilter.ALL to R.string.stayon_epg_filter_all,
                EpgBatchFilter.UNASSIGNED to R.string.stayon_epg_filter_unassigned,
                EpgBatchFilter.MAPPED to R.string.stayon_epg_filter_manual,
                EpgBatchFilter.PENDING to R.string.stayon_epg_filter_pending,
            ).forEach { (mode, label) ->
                StageButton(
                    text = stringResource(label),
                    tinted = filter == mode,
                    onClick = { filter = mode },
                    height = 44.mpx,
                    textSize = 15,
                )
            }
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
                        channel.epgChannelId.isNullOrBlank() -> stringResource(R.string.stayon_epg_batch_no_guide)
                        else -> stringResource(R.string.stayon_epg_batch_auto_id, channel.epgChannelId)
                    }
                    StageMenuItem(
                        text = channel.name,
                        value = if (sourceName.isBlank()) label else "$sourceName · $label",
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

private enum class EpgBatchFilter { ALL, UNASSIGNED, MAPPED, PENDING }
