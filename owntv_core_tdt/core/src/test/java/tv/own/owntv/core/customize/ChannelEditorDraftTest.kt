package tv.own.owntv.core.customize

import org.junit.Assert.*
import org.junit.Test

class ChannelEditorDraftTest {
    private fun row(id: Long, source: Long = 1, name: String = "Channel $id") =
        EditableChannel(id, source, name, name, false)
    private fun ChannelEditorDraft.ids() = rows.map { it.id }

    @Test fun nonAdjacentSelectionMovesAsStableBlockToExactFinalPosition() {
        val d = ChannelEditorDraft((1L..6L).map { row(it) })
        d.select(listOf(2, 5)); d.move(d.ids(), 3)
        assertEquals(listOf(1L, 3L, 2L, 5L, 4L, 6L), d.ids())
        d.move(d.ids(), Int.MAX_VALUE)
        assertEquals(listOf(1L, 3L, 4L, 6L, 2L, 5L), d.ids())
    }
    @Test fun filteredMovePreservesOtherSourceSlotsAndHiddenResults() {
        val d = ChannelEditorDraft(listOf(row(1), row(2, 2), row(3), row(4, 2), row(5)))
        d.select(listOf(5)); d.move(listOf(1, 3, 5), 1)
        assertEquals(listOf(5L, 2L, 1L, 4L, 3L), d.ids())
    }
    @Test fun rangeHandlesReverseEndpointsAndAddsToFreeSelection() {
        val d = ChannelEditorDraft((1L..8L).map { row(it) })
        d.select(listOf(8)); d.selectRange(d.ids(), 5, 2)
        assertEquals(setOf(2L, 3L, 4L, 5L, 8L), d.selected)
        d.invert(listOf(1, 2, 3))
        assertEquals(setOf(1L, 4L, 5L, 8L), d.selected)
    }
    @Test fun undoRedoRestoreAllFieldsAndEditingBranchesDropRedo() {
        val d = ChannelEditorDraft(listOf(row(1), row(2)))
        d.select(listOf(2)); d.rename { "New name" }; d.hide(true); d.move(d.ids(), 1)
        d.undo(); d.undo(); d.undo()
        assertFalse(d.dirty)
        d.redo(); assertEquals("New name", d.rows[1].name)
        d.hide(true); assertFalse(d.canRedo)
    }
    @Test fun blankNamesAreRejectedAndRestoreReturnsProviderName() {
        val d = ChannelEditorDraft(listOf(row(1, name = "ES | La 1 FHD"), row(2)))
        d.select(listOf(1)); d.rename { "  " }; assertFalse(d.dirty)
        d.rename { ChannelNameEdits.removeEnd(ChannelNameEdits.removeStart(it, "es |"), "fhd") }
        assertEquals("La 1", d.rows[0].name)
        assertEquals("Channel 2", d.rows[1].name)
        d.restoreNames(); assertFalse(d.dirty)
    }
    @Test fun unicodeCharacterCutsNeverLeaveHalfASurrogate() {
        assertEquals(" TV", ChannelNameEdits.cut("📺 TV", 1, true))
        assertEquals("TV ", ChannelNameEdits.cut("TV 📺", 1, false))
        assertEquals("", ChannelNameEdits.cut("📺", Int.MAX_VALUE, true))
    }
    @Test fun boundariesDoNotRemoveTextFromMiddleOfName() {
        assertEquals("La HD TV", ChannelNameEdits.removeEnd("La HD TV", "HD"))
        assertEquals("La HD TV", ChannelNameEdits.removeStart("La HD TV", "HD"))
    }
    @Test fun sortOnlyChangesSelectedSlotsAndNoOpDoesNotAddUndo() {
        val d = ChannelEditorDraft(listOf(row(1, name = "Z"), row(2, name = "Keep"), row(3, name = "A")))
        d.select(listOf(1, 3)); d.sortSelected(false)
        assertEquals(listOf(3L, 2L, 1L), d.ids())
        d.undo(); assertFalse(d.canUndo)
        d.select(emptyList()); d.move(d.ids(), 1); assertFalse(d.canUndo)
    }
}
