package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.customize.ChannelNameEdits
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private enum class EditorMenu { SOURCES, SEARCH, SELECT, ACTIONS }
private enum class EditorSelection { ALL, INVERT, NONE }

private enum class EditorAction(val label: Int) {
    RENAME(R.string.ce_rename), REMOVE_START(R.string.ce_remove_start), REMOVE_END(R.string.ce_remove_end),
    CUT_START(R.string.ce_cut_start), CUT_END(R.string.ce_cut_end),
    ADD_START(R.string.ce_add_start), ADD_END(R.string.ce_add_end),
    REPLACE(R.string.ce_replace), CLEAN(R.string.ce_clean), RESTORE(R.string.ce_restore),
    TOP(R.string.ce_top), BOTTOM(R.string.ce_bottom), POSITION(R.string.ce_position),
    UP(R.string.ce_up), DOWN(R.string.ce_down), BEFORE(R.string.ce_before), AFTER(R.string.ce_after),
    AZ(R.string.ce_az), ZA(R.string.ce_za), HIDE(R.string.ce_hide), SHOW(R.string.ce_show),
    REDO(R.string.ce_redo),
}

@Composable
fun ChannelEditorScreen(initialSource: Long?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ChannelEditorViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialSource) { vm.load(initialSource) }
    val visible = remember(state.rows, state.source, state.query) { vm.visibleRows() }
    var menu by remember { mutableStateOf<EditorMenu?>(null) }
    var action by remember { mutableStateOf<EditorAction?>(null) }
    var find by remember { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var placing by remember { mutableStateOf<Boolean?>(null) }
    var rangeAnchor by remember { mutableStateOf<Long?>(null) }
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val close: () -> Unit = { if (!state.busy) { if (state.dirty) discard = true else onBack() } }
    BackHandler { if (placing != null || rangeAnchor != null) { placing = null; rangeAnchor = null; vm.clear() } else close() }
    LaunchedEffect(state.busy) { if (!state.busy) runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(state.source, state.query) { listState.scrollToItem(0); placing = null; rangeAnchor = null }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_customize_title)),
        title = stringResource(R.string.ce_title),
        count = stringResource(R.string.ce_count, visible.size, state.selected.size),
        onBack = close, modifier = modifier, handleBack = false, settingsRoot = false,
        toolbar = {
            StageTool(stringResource(R.string.ce_sources), onClick = { if (!state.busy) menu = EditorMenu.SOURCES },
                icon = OwnTVIcon.LIST, boxed = true, modifier = Modifier.focusRequester(firstFocus))
            StageTool(stringResource(R.string.ce_search), onClick = { if (!state.busy) menu = EditorMenu.SEARCH }, boxed = true)
            StageTool(stringResource(R.string.ce_select), onClick = { if (!state.busy) menu = EditorMenu.SELECT }, boxed = true)
            StageTool(stringResource(R.string.ce_actions), onClick = { if (!state.busy) menu = EditorMenu.ACTIONS }, icon = OwnTVIcon.PENCIL, boxed = true)
            Spacer(Modifier.weight(1f))
            StageTool(stringResource(R.string.ce_undo), onClick = { vm.undo() }, boxed = true)
            StageTool(stringResource(R.string.common_save), onClick = { if (!state.busy && state.dirty) confirmSave = true }, boxed = true)
        },
        list = { pos ->
            Column(pos) {
                val hint = when {
                    state.busy -> R.string.ce_busy
                    placing != null -> R.string.ce_pick_target
                    rangeAnchor != null -> R.string.ce_range_end
                    state.saved -> R.string.ce_saved
                    else -> R.string.ce_hint
                }
                Text(stringResource(hint), style = stageText(20, 500))
                Spacer(Modifier.height(12.mpx))
                LazyColumn(state = listState) {
                    itemsIndexed(visible, key = { _, row -> row.id }) { index, row ->
                        StageSettingRow(
                            icon = if (row.id in state.selected) OwnTVIcon.CHECK else OwnTVIcon.LIVE_TV,
                            title = stringResource(R.string.ce_row, index + 1, row.name),
                            desc = listOfNotNull(state.sources[row.sourceId],
                                row.originalName.takeIf { it != row.name },
                                if (row.hidden) stringResource(R.string.ce_hidden) else null)
                                .joinToString(stringResource(R.string.content_metadata_separator)),
                            value = SettingValue.Action(if (row.id in state.selected) stringResource(R.string.ce_selected) else stringResource(R.string.ce_select)),
                            onClick = {
                                val targetAfter = placing
                                if (targetAfter != null) { vm.moveRelative(row.id, targetAfter); placing = null }
                                else { vm.toggle(row.id); rangeAnchor = null }
                            },
                            onLongClick = { vm.rangeStart(row.id); rangeAnchor = row.id },
                            marked = row.id in state.selected, enabled = !state.busy,
                        )
                    }
                }
            }
        },
    )
    when (menu) {
        EditorMenu.SOURCES -> PickerDialog(stringResource(R.string.ce_sources),
            listOf(EditorSelection.ALL.name to stringResource(R.string.ce_all_sources)) + state.sources.map { it.key.toString() to it.value },
            state.source?.toString() ?: EditorSelection.ALL.name, onSelect = { vm.source(it.toLongOrNull()); menu = null }, onDismiss = { menu = null })
        EditorMenu.SEARCH -> TextInputDialog(stringResource(R.string.ce_search), initial = state.query,
            onConfirm = { vm.search(it); menu = null }, onDismiss = { menu = null }, hint = stringResource(R.string.ce_search_hint))
        EditorMenu.SELECT -> PickerDialog(stringResource(R.string.ce_select), listOf(
            EditorSelection.ALL.name to stringResource(R.string.ce_select_all), EditorSelection.INVERT.name to stringResource(R.string.ce_invert),
            EditorSelection.NONE.name to stringResource(R.string.ce_clear)), "",
            onSelect = { when (it) { EditorSelection.ALL.name -> vm.selectAll(); EditorSelection.INVERT.name -> vm.invert(); else -> vm.clear() }; menu = null; rangeAnchor = null },
            onDismiss = { menu = null })
        EditorMenu.ACTIONS -> PickerDialog(stringResource(R.string.ce_actions), EditorAction.entries.map { it.name to stringResource(it.label) }, "",
            onSelect = { key ->
                menu = null
                val selectedAction = EditorAction.valueOf(key)
                if (selectedAction != EditorAction.REDO && state.selected.isEmpty()) notice = R.string.ce_need_selection
                else when (selectedAction) {
                    EditorAction.TOP -> vm.move(1)
                    EditorAction.BOTTOM -> vm.move(visible.size)
                    EditorAction.UP -> vm.moveStep(-1)
                    EditorAction.DOWN -> vm.moveStep(1)
                    EditorAction.BEFORE -> placing = false
                    EditorAction.AFTER -> placing = true
                    EditorAction.AZ -> vm.sort(false)
                    EditorAction.ZA -> vm.sort(true)
                    EditorAction.HIDE -> vm.hide(true)
                    EditorAction.SHOW -> vm.hide(false)
                    EditorAction.RESTORE -> vm.restoreNames()
                    EditorAction.CLEAN -> vm.rename(ChannelNameEdits::clean)
                    EditorAction.REDO -> vm.redo()
                    else -> action = selectedAction
                }
            }, onDismiss = { menu = null }, searchable = true)
        null -> Unit
    }
    action?.let { current ->
        TextInputDialog(stringResource(current.label), allowBlank = false,
            hint = stringResource(if (current in listOf(EditorAction.CUT_START, EditorAction.CUT_END, EditorAction.POSITION)) R.string.ce_number_hint else R.string.ce_text_hint),
            onConfirm = { text ->
                when (current) {
                    EditorAction.RENAME -> vm.rename { text }
                    EditorAction.REMOVE_START -> vm.rename { ChannelNameEdits.removeStart(it, text) }
                    EditorAction.REMOVE_END -> vm.rename { ChannelNameEdits.removeEnd(it, text) }
                    EditorAction.ADD_START -> vm.rename { ChannelNameEdits.addStart(it, text) }
                    EditorAction.ADD_END -> vm.rename { ChannelNameEdits.addEnd(it, text) }
                    EditorAction.CUT_START, EditorAction.CUT_END, EditorAction.POSITION -> {
                        val number = text.toIntOrNull()?.takeIf { it > 0 }
                        if (number == null) notice = R.string.ce_invalid_number
                        else if (current == EditorAction.POSITION) vm.move(number)
                        else vm.rename { ChannelNameEdits.cut(it, number, current == EditorAction.CUT_START) }
                    }
                    EditorAction.REPLACE -> find = text
                    else -> Unit
                }
                action = null
            }, onDismiss = { action = null })
    }
    find?.let { pattern -> TextInputDialog(stringResource(R.string.ce_replace_with), hint = stringResource(R.string.ce_replace_hint),
        onConfirm = { value -> vm.rename { it.replace(pattern, value, ignoreCase = true) }; find = null }, onDismiss = { find = null }) }
    if (confirmSave || discard) {
        val review = vm.preview()
        StagePopup(onDismiss = { confirmSave = false; discard = false },
            title = stringResource(if (discard) R.string.ce_discard else R.string.ce_review),
            body = stringResource(if (discard) R.string.ce_discard_body else R.string.ce_review_body, review.size),
            width = 880.mpx,
            buttons = {
                StageButton(stringResource(R.string.common_cancel), onClick = { confirmSave = false; discard = false })
                StageButton(stringResource(if (discard) R.string.ce_discard else R.string.common_save), tinted = true,
                    onClick = { if (discard) onBack() else vm.save(); discard = false; confirmSave = false })
            },
        ) {
            if (!discard) review.take(8).forEach { entry ->
                Text(stringResource(R.string.ce_preview_row, entry.before.name, entry.after.name,
                    stringResource(if (entry.after.hidden) R.string.ce_hidden else R.string.ce_visible), entry.from, entry.to), style = stageText(20, 500))
            }
        }
    }
    val problemText = state.problem?.let { when (it) {
        ChannelEditorViewModel.Problem.LOAD -> R.string.ce_load_error
        ChannelEditorViewModel.Problem.TOO_LARGE -> R.string.ce_too_large
        ChannelEditorViewModel.Problem.CHANGED -> R.string.ce_changed
        ChannelEditorViewModel.Problem.SAVE -> R.string.ce_save_error
    } } ?: notice
    problemText?.let { message ->
        StagePopup(onDismiss = { notice = null; vm.clearProblem() }, title = stringResource(R.string.ce_title),
            body = stringResource(message), width = 820.mpx, buttons = {
                StageButton(stringResource(R.string.settings_close), onClick = { notice = null; vm.clearProblem() })
            }) {}
    }
}
