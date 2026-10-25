package tv.own.owntv.features.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThemeCatalogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { DownloadableThemes.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { store.refresh(); first.requestFocus() }
    StagePopup(title = stringResource(R.string.themes_title), onDismiss = onDismiss, width = 1360.mpx,
        buttons = {
            StageButton(stringResource(R.string.themes_refresh), { if (!state.busy) store.refresh() }, height = 56.mpx, textSize = 18)
            StageButton(stringResource(R.string.settings_close), onDismiss, height = 56.mpx, textSize = 18)
        }) {
        Text(stringResource(R.string.themes_description), style = stageText(19, 400), color = StageColors.Muted)
        Spacer(Modifier.height(16.mpx))
        StageButton(stringResource(if (state.selected.isEmpty()) R.string.themes_original_active else R.string.themes_original),
            { if (!state.busy) store.select("") }, modifier = Modifier.focusRequester(first), textSize = 20)
        if (state.busy) Text(stringResource(R.string.themes_busy), style = stageText(18, 400), color = StageColors.Muted)
        if (state.failed) Text(stringResource(R.string.themes_failure), style = stageText(18, 400), color = StageColors.Warn)
        val ids = state.catalog.map { it.id } + state.installed.keys.filter { id -> state.catalog.none { it.id == id } }
        ids.forEach { id ->
            val entry = state.catalog.firstOrNull { it.id == id }
            val installed = state.installed[id]
            val name = entry?.name ?: installed!!.theme.name
            val preview = entry?.preview ?: listOf(installed!!.theme.colors.getValue("page"), installed.theme.colors.getValue("accent"), installed.theme.colors.getValue("secondary"))
            val bring = remember(id) { BringIntoViewRequester() }
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxWidth().padding(top = 18.mpx)
                .bringIntoViewRequester(bring).onFocusChanged { if (it.hasFocus) scope.launch { bring.bringIntoView() } }
                .background(StageColors.Ink.copy(alpha = .04f), RoundedCornerShape(20.mpx)).padding(20.mpx)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                    // Preview uses the remote swatches, never downloads a wallpaper before selection.
                    Column(Modifier.width(240.mpx).height(112.mpx)
                        .background(Color(preview[0]), RoundedCornerShape(14.mpx))
                        .border(1.mpx, Color(preview[1]), RoundedCornerShape(14.mpx)).padding(14.mpx)) {
                        Text("Channel +", color = Color(preview[1]), style = stageText(18, 700))
                        Row(Modifier.padding(top = 12.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                            repeat(3) { Box(Modifier.weight(1f).height(30.mpx).background(Color(preview[2]).copy(alpha = .45f), RoundedCornerShape(5.mpx))) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = stageText(25, 700), color = StageColors.Text)
                        Text(if (state.selected == id) stringResource(R.string.themes_active) else if (installed != null) stringResource(R.string.themes_downloaded) else stringResource(R.string.themes_size, ((entry!!.totalBytes + 1023) / 1024).toString()),
                            style = stageText(18, 400), color = StageColors.Muted)
                    }
                    if (entry != null && (installed == null || entry.version > installed.theme.version))
                        StageButton(stringResource(R.string.themes_download), { if (!state.busy) store.download(entry) }, height = 56.mpx, textSize = 18)
                    if (installed != null) {
                        StageButton(stringResource(R.string.themes_apply), { if (!state.busy) store.select(id) }, height = 56.mpx, textSize = 18, tinted = true)
                        StageButton(stringResource(R.string.themes_delete), { if (!state.busy) store.remove(id) }, height = 56.mpx, textSize = 18)
                    }
                }
            }
        }
    }
}
