package com.cartoonstudio.core.undo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoStackTest {

    private fun command(before: String, after: String, mergeKey: String? = null) =
        SnapshotCommand("edit", before, after, mergeKey)

    @Test
    fun `execute applies and records`() {
        val stack = UndoStack<String>()
        val result = stack.execute("a", command("a", "b"))
        assertTrue(result.applied)
        assertEquals("b", result.state)
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `undo and redo restore exact states`() {
        val stack = UndoStack<String>()
        var state = stack.execute("a", command("a", "b")).state
        state = stack.execute(state, command("b", "c")).state
        assertEquals("c", state)

        state = stack.undo(state)
        assertEquals("b", state)
        state = stack.undo(state)
        assertEquals("a", state)
        assertFalse(stack.canUndo)

        state = stack.redo(state)
        assertEquals("b", state)
    }

    @Test
    fun `rejected commands never touch history`() {
        val stack = UndoStack<String>()
        val rejecting = object : Command<String> {
            override val label = "nope"
            override fun validate(state: String) = "not allowed"
            override fun apply(state: String) = "changed"
            override fun revert(state: String) = state
        }
        val result = stack.execute("a", rejecting)
        assertFalse(result.applied)
        assertEquals("a", result.state)
        assertEquals("not allowed", result.rejection)
        assertFalse(stack.canUndo)
    }

    @Test
    fun `commands with the same merge key collapse into one entry`() {
        var now = 1_000L
        val stack = UndoStack<String>(now = { now })
        var state = stack.execute("a", command("a", "b", "slider")).state
        now += 100
        state = stack.execute(state, command("b", "c", "slider")).state
        assertEquals("c", state)
        assertEquals(1, stack.state().entries.size)

        // Undo must jump all the way back to the pre-drag value.
        assertEquals("a", stack.undo(state))
    }

    @Test
    fun `merging stops once the window closes`() {
        var now = 1_000L
        val stack = UndoStack<String>(now = { now })
        var state = stack.execute("a", command("a", "b", "slider")).state
        now += 5_000
        state = stack.execute(state, command("b", "c", "slider")).state
        assertEquals(2, stack.state().entries.size)
        assertEquals("b", stack.undo(state))
    }

    @Test
    fun `a new edit clears the redo branch`() {
        val stack = UndoStack<String>()
        var state = stack.execute("a", command("a", "b")).state
        state = stack.undo(state)
        assertTrue(stack.canRedo)
        stack.execute(state, command("a", "z"))
        assertFalse(stack.canRedo)
    }
}
