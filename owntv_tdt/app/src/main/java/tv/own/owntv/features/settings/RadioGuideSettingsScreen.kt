package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RadioGuideSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { RadioGuideStore.shared(context) }
    val feeds by store.feeds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var form by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<RadioGuideFeed?>(null) }
    var selected by remember { mutableStateOf<RadioGuideFeed?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(store) { store.load() }
    LaunchedEffect(form) { runCatching { focus.requestFocus() } }

    fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun openForm(feed: RadioGuideFeed?) {
        edit = feed
        name = feed?.name.orEmpty()
        url = feed?.url.orEmpty()
        failed = false
        form = true
    }

    StageFullPage(
        parents = listOf(stringResource(R.string.stayon_settings_sources)),
        title = stringResource(R.string.stayon_radio_epg),
        count = feeds.size.toString(),
        onBack = { if (form) form = false else onBack() },
        rowsFocus = focus,
        modifier = modifier,
    ) {
        if (busy) StageSettingsNote(stringResource(R.string.settings_sources_importing), null)
        if (failed) StageSettingsNote(stringResource(R.string.stayon_radio_epg_error), null)
        if (form) {
            StageFieldRow(icon = OwnTVIcon.PENCIL, label = stringResource(R.string.stayon_radio_name), value = name,
                onValueChange = { name = it }, help = null)
            StageFieldRow(icon = OwnTVIcon.NETWORK, label = stringResource(R.string.stayon_radio_epg_url), value = url,
                onValueChange = { url = it }, help = null)
            StageSettingRow(OwnTVIcon.CHECK, stringResource(R.string.stayon_radio_save), null, null,
                enabled = !busy && name.isNotBlank() && StayonRadioLibrary.isStreamUrl(url),
                onClick = { runOperation { store.save(edit?.id, name.trim(), url.trim()); form = false } })
        } else {
            StageSettingRow(OwnTVIcon.ADD, stringResource(R.string.content_epg_add),
                stringResource(R.string.stayon_radio_epg_desc), SettingValue.Opens(null),
                enabled = !busy && feeds.size < 4, onClick = { openForm(null) })
            feeds.forEachIndexed { index, feed ->
                StageSettingRow(OwnTVIcon.EPG, feed.name,
                    stringResource(R.string.settings_synced_at, DateFormat.getDateTimeInstance().format(Date(feed.updated))),
                    SettingValue.Opens((index + 1).toString()), enabled = !busy, onClick = { selected = feed })
            }
        }
    }

    selected?.let { feed ->
        val index = feeds.indexOfFirst { it.id == feed.id }
        SettingsActionPopup(feed.name, listOfNotNull(
            StageAction(OwnTVIcon.PENCIL, stringResource(R.string.common_edit), { openForm(feed) }),
            StageAction(OwnTVIcon.REFRESH, stringResource(R.string.stayon_radio_refresh),
                { runOperation { store.save(feed.id, feed.name, feed.url) } }),
            if (index > 0) StageAction(OwnTVIcon.CHEVRON_UP, stringResource(R.string.settings_row_menu_move_up),
                { runOperation { store.move(feed.id, -1) } }) else null,
            if (index in 0 until feeds.lastIndex) StageAction(OwnTVIcon.CHEVRON_DOWN, stringResource(R.string.settings_row_menu_move_down),
                { runOperation { store.move(feed.id, 1) } }) else null,
            StageAction(OwnTVIcon.TRASH, stringResource(R.string.common_delete), { runOperation { store.remove(feed.id) } }, danger = true),
        )) { selected = null }
    }
}
