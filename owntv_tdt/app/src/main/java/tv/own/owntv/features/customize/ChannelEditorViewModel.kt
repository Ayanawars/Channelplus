package tv.own.owntv.features.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.ChannelEditorDraft
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.EditableChannel
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

class ChannelEditorViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val orders: ContentOrderDao,
    private val customization: CustomizationStore,
) : ViewModel() {
    enum class Problem { LOAD, TOO_LARGE, CHANGED, SAVE }
    data class State(
        val rows: List<EditableChannel> = emptyList(), val selected: Set<Long> = emptySet(),
        val sources: Map<Long, String> = emptyMap(), val source: Long? = null, val query: String = "",
        val dirty: Boolean = false, val canUndo: Boolean = false, val canRedo: Boolean = false,
        val busy: Boolean = true, val problem: Problem? = null, val saved: Boolean = false,
    )
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var draft: ChannelEditorDraft? = null
    private var entities = emptyMap<Long, ChannelEntity>()
    private var initialCustom = SectionCustomizations()
    private var profileId = -1L
    private var sourceIds = emptyList<Long>()
    private var anchor: Long? = null
    private var folderKeys = emptyMap<Long, String>()

    fun load(initialSource: Long?) {
        _state.value = State(source = initialSource)
        draft = null
        viewModelScope.launch {
            try {
                profileId = settings.activeProfileId.first { it >= 0 }
                val profileSources = sourceDao.observeForProfile(profileId).first()
                sourceIds = profileSources.map { it.id }
                val channels = channelDao.snapshotAllManual(sourceIds.ifEmpty { listOf(-1L) }, profileId,
                    ContentOrderEntity.ALL_CHANNELS_CONTEXT, MAX_ROWS + 1)
                if (channels.size > MAX_ROWS) {
                    _state.value = _state.value.copy(busy = false, problem = Problem.TOO_LARGE)
                    return@launch
                }
                initialCustom = customization.observe(profileId, MediaType.LIVE).first()
                entities = channels.associateBy { it.id }
                folderKeys = categoryDao.observe(sourceIds.ifEmpty { listOf(-1L) }, MediaType.LIVE).first()
                    .associate { it.id to CustomizeKeys.category(it) }
                draft = ChannelEditorDraft(channels.map { ch ->
                    val key = CustomizeKeys.channel(ch)
                    EditableChannel(ch.id, ch.sourceId, ch.name, initialCustom.itemNames[key] ?: ch.name,
                        key in initialCustom.hiddenItems)
                })
                _state.value = _state.value.copy(busy = false,
                    sources = profileSources.associate { it.id to it.name })
                publish()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(busy = false, problem = Problem.LOAD) }
        }
    }
    private fun publish() {
        val d = draft ?: return
        val filter = _state.value
        val visibleIds = d.rows.filter { (filter.source == null || it.sourceId == filter.source) &&
            (filter.query.isBlank() || it.name.contains(filter.query, true) || it.originalName.contains(filter.query, true)) }.map { it.id }.toSet()
        d.select(d.selected.intersect(visibleIds))
        _state.value = _state.value.copy(rows = d.rows, selected = d.selected, dirty = d.dirty,
            canUndo = d.canUndo, canRedo = d.canRedo, saved = false)
    }
    fun visibleRows(): List<EditableChannel> {
        val s = _state.value
        return s.rows.filter { (s.source == null || it.sourceId == s.source) &&
            (s.query.isBlank() || it.name.contains(s.query, true) || it.originalName.contains(s.query, true)) }
    }
    private fun edit(block: ChannelEditorDraft.() -> Unit) {
        if (_state.value.busy) return
        draft?.block(); publish()
    }
    fun source(id: Long?) { if (!_state.value.busy) { _state.value = _state.value.copy(source = id); clear() } }
    fun search(query: String) { if (!_state.value.busy) { _state.value = _state.value.copy(query = query); clear() } }
    fun toggle(id: Long) = edit {
        val start = anchor
        if (start != null) { selectRange(visibleRows().map { it.id }, start, id); anchor = null } else toggle(id)
    }
    fun rangeStart(id: Long) { if (!_state.value.busy) { anchor = id; edit { select(listOf(id)) } } }
    fun clear() { anchor = null; edit { clearSelection() } }
    fun selectAll() = edit { select(visibleRows().map { it.id }) }
    fun invert() = edit { invert(visibleRows().map { it.id }) }
    fun rename(transform: (String) -> String) = edit { rename(transform) }
    fun restoreNames() = edit { restoreNames() }
    fun hide(hidden: Boolean) = edit { hide(hidden) }
    fun move(position: Int) = edit { move(visibleRows().map { it.id }, position) }
    fun moveStep(delta: Int) {
        val rows = visibleRows()
        val first = rows.indexOfFirst { it.id in _state.value.selected }
        if (first >= 0) move(first + 1 + delta)
    }
    fun moveRelative(target: Long, after: Boolean) {
        val rest = visibleRows().filterNot { it.id in _state.value.selected }
        val index = rest.indexOfFirst { it.id == target }
        if (index >= 0) move(index + 1 + if (after) 1 else 0)
    }
    fun sort(descending: Boolean) = edit { sortSelected(descending) }
    fun undo() = edit { undo() }
    fun redo() = edit { redo() }
    fun clearProblem() { _state.value = _state.value.copy(problem = null) }
    data class Review(val before: EditableChannel, val after: EditableChannel, val from: Int, val to: Int)
    fun preview(): List<Review> {
        val d = draft ?: return emptyList()
        val before = d.baseline.associateBy { it.id }
        val positions = d.baseline.mapIndexed { i, row -> row.id to i }.toMap()
        return d.rows.mapIndexedNotNull { i, row -> before[row.id]?.let { old ->
            if (old != row || positions[row.id] != i) Review(old, row, (positions[row.id] ?: i) + 1, i + 1) else null
        } }
    }
    fun save() {
        val d = draft ?: return
        if (_state.value.busy || !d.dirty) return
        _state.value = _state.value.copy(busy = true, problem = null)
        viewModelScope.launch {
            try {
                val currentProfile = settings.activeProfileId.first()
                val currentSources = sourceDao.observeForProfile(profileId).first()
                val fresh = channelDao.snapshotAllManual(sourceIds.ifEmpty { listOf(-1L) }, profileId,
                    ContentOrderEntity.ALL_CHANNELS_CONTEXT, MAX_ROWS + 1)
                val originalIds = d.baseline.map { it.id }
                val desiredIds = d.rows.map { it.id }
                if (currentProfile != profileId || currentSources.map { it.id }.toSet() != sourceIds.toSet() ||
                    fresh.map { it.id }.let { it != originalIds && it != desiredIds } ||
                    fresh.any { it != entities[it.id] }) {
                    _state.value = _state.value.copy(busy = false, problem = Problem.CHANGED)
                    return@launch
                }
                val before = d.baseline.associateBy { it.id }
                val renamed = d.rows.filter { it.name != before[it.id]?.name }
                val visibility = d.rows.filter { it.hidden != before[it.id]?.hidden }
                // One DataStore edit, merging only touched keys; concurrent unrelated edits survive.
                customization.update(profileId, MediaType.LIVE) { current ->
                    var names = current.itemNames
                    var hidden = current.hiddenItems
                    renamed.forEach { row ->
                        val key = CustomizeKeys.channel(entities.getValue(row.id))
                        val expected = row.name.takeUnless { it == row.originalName }
                        check(current.itemNames[key] == initialCustom.itemNames[key] || current.itemNames[key] == expected)
                        names = if (row.name == row.originalName) names - key else names + (key to row.name)
                    }
                    visibility.forEach { row ->
                        val key = CustomizeKeys.channel(entities.getValue(row.id))
                        check((key in current.hiddenItems) == (key in initialCustom.hiddenItems) || (key in current.hiddenItems) == row.hidden)
                        hidden = if (row.hidden) hidden + (key to row.originalName) else hidden - key
                    }
                    current.copy(itemNames = names, hiddenItems = hidden)
                }
                if (originalIds != desiredIds) {
                    val contexts = mutableMapOf(ContentOrderEntity.ALL_CHANNELS_CONTEXT to desiredIds)
                    val baselineFolders = d.baseline.groupBy { entities[it.id]?.categoryId }
                    d.rows.groupBy { entities[it.id]?.categoryId }.forEach { (categoryId, rows) ->
                        val key = categoryId?.let(folderKeys::get)
                        if (key != null && rows.map { it.id } != baselineFolders[categoryId].orEmpty().map { it.id })
                            contexts[key] = rows.map { it.id }
                    }
                    orders.replaceContexts(profileId, MediaType.LIVE, contexts.mapValues { (key, ids) ->
                        ids.mapIndexed { i, id -> ContentOrderEntity(profileId = profileId, mediaType = MediaType.LIVE,
                            contextKey = key, itemId = id, position = i) }
                    })
                }
                initialCustom = customization.observe(profileId, MediaType.LIVE).first()
                draft = ChannelEditorDraft(d.rows)
                publish()
                _state.value = _state.value.copy(busy = false, saved = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(busy = false, problem = Problem.SAVE) }
        }
    }
    companion object { private const val MAX_ROWS = 20_000 }
}
