package tv.own.owntv.core.customize

/** Local editing only. Source URLs and provider names never enter this draft. */
data class EditableChannel(
    val id: Long,
    val sourceId: Long,
    val originalName: String,
    val name: String,
    val hidden: Boolean,
)

/** Immutable snapshots make undo restore order, names and visibility together. */
class ChannelEditorDraft(initial: List<EditableChannel>) {
    val baseline = initial.toList()
    var rows = baseline
        private set
    var selected: Set<Long> = emptySet()
        private set
    private val past = ArrayDeque<List<EditableChannel>>()
    private val future = ArrayDeque<List<EditableChannel>>()
    val dirty get() = rows != baseline
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    val changedCount get() = rows.mapIndexed { i, row -> row.id.takeIf { row != baseline.getOrNull(i) } }.count { it != null }

    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun clearSelection() { selected = emptySet() }
    fun select(ids: Collection<Long>) { selected = ids.toSet() }
    fun invert(ids: Collection<Long>) { selected = (selected - ids.toSet()) + (ids.toSet() - selected) }
    fun selectRange(scope: List<Long>, anchor: Long, end: Long) {
        val a = scope.indexOf(anchor); val b = scope.indexOf(end)
        if (a < 0 || b < 0) return
        selected = selected + scope.subList(minOf(a, b), maxOf(a, b) + 1)
    }
    private fun commit(next: List<EditableChannel>) {
        if (next == rows) return
        past.addLast(rows)
        if (past.size > 30) past.removeFirst()
        future.clear()
        rows = next
    }
    fun undo() { if (past.isNotEmpty()) { future.addLast(rows); rows = past.removeLast() } }
    fun redo() { if (future.isNotEmpty()) { past.addLast(rows); rows = future.removeLast() } }
    fun rename(transform: (String) -> String) {
        commit(rows.map { row ->
            if (row.id !in selected) row else transform(row.name).trim().takeIf { it.isNotBlank() }
                ?.let { row.copy(name = it) } ?: row
        })
    }
    fun restoreNames() { commit(rows.map { if (it.id in selected) it.copy(name = it.originalName) else it }) }
    fun hide(hidden: Boolean) { commit(rows.map { if (it.id in selected) it.copy(hidden = hidden) else it }) }

    /** Target is the final 1-based position of the first selected row within the visible scope.
     * Other sources/filtered-out rows keep their slots. The block retains its previous relative order. */
    fun move(scope: List<Long>, position: Int) {
        val scopeSet = scope.toSet()
        val subset = rows.filter { it.id in scopeSet }
        val block = subset.filter { it.id in selected }
        if (block.isEmpty()) return
        val rest = subset.filterNot { it.id in selected }.toMutableList()
        rest.addAll((position - 1).coerceIn(0, rest.size), block)
        val replacement = rest.iterator()
        commit(rows.map { if (it.id in scopeSet) replacement.next() else it })
    }
    fun sortSelected(descending: Boolean) {
        val sorted = rows.filter { it.id in selected }.sortedWith(compareBy<EditableChannel> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
        val replacement = (if (descending) sorted.reversed() else sorted).iterator()
        commit(rows.map { if (it.id in selected) replacement.next() else it })
    }
}

object ChannelNameEdits {
    fun addStart(name: String, text: String): String = text + ' ' + name
    fun addEnd(name: String, text: String): String = name + ' ' + text
    fun removeStart(name: String, text: String): String =
        if (text.isNotEmpty() && name.startsWith(text, ignoreCase = true)) name.substring(text.length).trim() else name
    fun removeEnd(name: String, text: String): String =
        if (text.isNotEmpty() && name.endsWith(text, ignoreCase = true)) name.dropLast(text.length).trim() else name
    fun cut(name: String, count: Int, start: Boolean): String {
        val n = count.coerceIn(0, name.codePointCount(0, name.length))
        return if (start) name.substring(name.offsetByCodePoints(0, n))
        else name.substring(0, name.offsetByCodePoints(name.length, -n))
    }
    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")
}
