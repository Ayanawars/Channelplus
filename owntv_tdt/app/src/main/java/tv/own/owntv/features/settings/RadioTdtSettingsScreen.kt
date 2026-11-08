package tv.own.owntv.features.settings

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvView
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.core.model.TifChannelUri
import tv.own.owntv.core.sync.TunerRadioClassification
import tv.own.owntv.features.setup.TunerSourceOption
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup

@Composable
internal fun RadioTdtSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember(context) { StayonRadioLibrary.shared(context) }
    val allStations by library.stations.collectAsStateWithLifecycle()
    val stations = allStations.filter { it.tunerChannel != null }
    val scope = rememberCoroutineScope()
    val settings: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val sources by settings.sources.collectAsStateWithLifecycle()
    var scan by remember { mutableStateOf<Pair<String, List<ScannedService>>?>(null) }
    val firstFocus = remember { FocusRequester() }
    var selected by remember { mutableStateOf<StayonRadioStation?>(null) }
    var removing by remember { mutableStateOf<StayonRadioStation?>(null) }
    var playing by remember { mutableStateOf<StayonRadioStation?>(null) }
    var status by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    StageFullPage(
        parents = listOf(stringResource(R.string.stayon_settings_sources)),
        title = stringResource(R.string.stayon_radio_tdt),
        count = stations.size.toString(),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = firstFocus,
    ) {
        TunerSourceOption(radio = true) { _, sourceUrl ->
            if (!loading) {
                val inputId = Uri.parse(sourceUrl).getQueryParameter("input") ?: return@TunerSourceOption
                loading = true
                scope.launch {
                    try {
                        val rows = withContext(Dispatchers.IO) { readTunerRadio(context, inputId) }
                        if (rows.isNotEmpty()) scan = inputId to rows
                        status = if (rows.isEmpty()) R.string.stayon_radio_tdt_empty else 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        status = R.string.stayon_radio_tdt_failed
                    } finally {
                        loading = false
                    }
                }
            }
        }
        if (loading) StageSettingsNote(stringResource(R.string.settings_sources_importing), null)
        else if (status != 0) StageSettingsNote(stringResource(status), null)
        stations.forEach { station ->
            StageSettingRow(
                icon = OwnTVIcon.AUDIO,
                title = station.name,
                desc = null,
                value = SettingValue.Opens(null),
                onClick = { selected = station },
            )
        }
    }

    scan?.let { (inputId, rows) ->
        ClassifyTunerServices(rows, onDismiss = { scan = null }, onSave = { radioIds ->
            TunerRadioClassification.save(context, inputId, rows.map { it.channelId }.toSet(), radioIds)
            library.replaceTunerStations(inputId, rows.filter { it.channelId in radioIds }.map { it.station })
            sources.filter { source ->
                tv.own.owntv.core.sync.TunerSourceSyncer.isSource(source.url) &&
                    Uri.parse(source.url).getQueryParameter("input") == inputId
            }.forEach { settings.resync(it) }
            status = if (radioIds.isEmpty()) R.string.stayon_radio_none_selected else 0
            scan = null
        })
    }

    selected?.let { station ->
        val first = remember { FocusRequester() }
        LaunchedEffect(station.id) { runCatching { first.requestFocus() } }
        StagePopup(onDismiss = { selected = null }, title = station.name) {
            StageActionColumn(
                listOf(
                    StageAction(OwnTVIcon.PLAY, stringResource(R.string.stayon_radio_listen), {
                        selected = null
                        playing = station
                    }),
                    StageAction(OwnTVIcon.TRASH, stringResource(R.string.settings_sources_delete), {
                        selected = null
                        removing = station
                    }, danger = true),
                ),
                back = null,
                first = first,
            )
        }
    }
    removing?.let { station ->
        ConfirmDialog(
            title = stringResource(R.string.stayon_radio_tdt_remove, station.name),
            message = stringResource(R.string.stayon_radio_tdt_remove_description),
            onConfirm = { library.deleteStation(station.id); removing = null },
            onDismiss = { removing = null },
        )
    }
    playing?.let { station -> TunerRadioPlayback(station) { playing = null } }
}

private data class ScannedService(val channelId: Long, val station: StayonRadioStation, val suggestedRadio: Boolean)

@Composable
private fun ClassifyTunerServices(
    rows: List<ScannedService>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var radioIds by remember(rows) { mutableStateOf(rows.filter { it.suggestedRadio }.map { it.channelId }.toSet()) }
    val first = remember { FocusRequester() }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.stayon_radio_classify),
        body = stringResource(R.string.stayon_radio_classify_desc),
        scroll = false,
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onDismiss)
            StageButton(stringResource(R.string.stayon_radio_save), { onSave(radioIds) }, tinted = true)
        },
    ) {
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            items(rows, key = { it.channelId }) { row ->
                val radio = row.channelId in radioIds
                StageSettingRow(
                    icon = if (radio) OwnTVIcon.AUDIO else OwnTVIcon.LIVE_TV,
                    title = row.station.name,
                    desc = null,
                    value = SettingValue.Choice(stringResource(if (radio) R.string.stayon_nav_radio else R.string.stayon_tv)),
                    onClick = { radioIds = if (radio) radioIds - row.channelId else radioIds + row.channelId },
                    modifier = if (row == rows.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

private fun readTunerRadio(context: Context, inputId: String): List<ScannedService> {
    val columns = arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME,
        TvContract.Channels.COLUMN_SERVICE_TYPE)
    val uri = TvContract.buildChannelsUriForInput(inputId)
    val saved = TunerRadioClassification.read(context, inputId)
    return context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: id.toString()
                val channel = TifChannelUri(inputId, id).encode()
                val station = StayonRadioStation(channel, name, channel, TvContract.buildChannelLogoUri(id).toString())
                add(ScannedService(id, station, saved[id] ?: TunerRadioClassification.suggestRadio(name, cursor.getString(2))))
            }
        }.sortedWith(compareByDescending<ScannedService> { it.suggestedRadio }.thenBy { it.station.name })
    }.orEmpty()
}

/** Own a tuner session only while the radio player is visible. */
@Composable
internal fun TunerRadioPlayback(station: StayonRadioStation, onClose: () -> Unit) {
    val channel = station.tunerChannel ?: return
    val context = LocalContext.current
    val stopFocus = remember { FocusRequester() }
    val close by rememberUpdatedState(onClose)
    var failed by remember(channel) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, channel) {
        RadioAudioPlayer.stop()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) close()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(channel) { runCatching { stopFocus.requestFocus() } }
    StagePopup(
        onDismiss = onClose,
        title = station.name,
        body = stringResource(if (failed) R.string.stayon_radio_tdt_failed else R.string.stayon_radio_tdt),
        buttons = {
            StageButton(stringResource(R.string.stayon_radio_stop), close, Modifier.focusRequester(stopFocus))
        },
    ) {
        if (!failed) AndroidView(
            factory = {
                TvView(context).also { tuner ->
                    tuner.setCallback(object : TvView.TvInputCallback() {
                        override fun onConnectionFailed(inputId: String) { failed = true }
                        override fun onDisconnected(inputId: String) { failed = true }
                        override fun onContentBlocked(inputId: String, rating: android.media.tv.TvContentRating) { failed = true }
                    })
                    runCatching { tuner.tune(channel.inputId, TvContract.buildChannelUri(channel.channelId)) }
                        .onFailure { failed = true }
                }
            },
            onRelease = { tuner -> tuner.setCallback(null); tuner.reset() },
            modifier = Modifier.fillMaxWidth().height(1.dp),
        )
    }
}
