package com.cartoonstudio.core.undo

/** A single entry in the visible edit history. */
data class HistoryEntry(val label: String, val timestampMillis: Long)

/** Snapshot of history state for the UI layer. */
data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val entries: List<HistoryEntry> = emptyList(),
    val revision: Long = 0L,
)

/**
 * Bounded undo/redo stack with automatic command coalescing.
 *
 * The stack is deliberately bounded so long drawing sessions cannot exhaust
 * memory; the oldest entries are dropped first.
 */
class UndoStack<S>(
    private val limit: Int = 200,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val undoStack = ArrayDeque<Command<S>>()
    private val redoStack = ArrayDeque<Command<S>>()
    private var lastCommandAt: Long = 0L
    private var revision: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Validates and applies [command], returning the new state, or the
     * unchanged state when validation fails.
     */
    fun execute(state: S, command: Command<S>): CommandResult<S> {
        val problem = command.validate(state)
        if (problem != null) return CommandResult(state, applied = false, rejection = problem)

        val newState = command.apply(state)
        record(command)
        return CommandResult(newState, applied = true, rejection = null)
    }

    /** Records an already-applied command (used by interactive gestures). */
    fun record(command: Command<S>) {
        redoStack.clear()
        val timestamp = now()
        val previous = undoStack.lastOrNull()
        val mergeable = previous != null &&
            command.mergeKey != null &&
            previous.mergeKey == command.mergeKey &&
            timestamp - lastCommandAt <= command.mergeWindowMillis

        if (mergeable && previous is SnapshotCommand<S> && command is SnapshotCommand<S>) {
            undoStack.removeLast()
            undoStack.addLast(previous.mergedWith(command))
        } else {
            undoStack.addLast(command)
            while (undoStack.size > limit) undoStack.removeFirst()
        }
        lastCommandAt = timestamp
        revision += 1
    }

    fun undo(state: S): S {
        val command = undoStack.removeLastOrNull() ?: return state
        redoStack.addLast(command)
        revision += 1
        // Breaking the merge window prevents a new edit from folding into the
        // command we just undid.
        lastCommandAt = 0L
        return command.revert(state)
    }

    fun redo(state: S): S {
        val command = redoStack.removeLastOrNull() ?: return state
        undoStack.addLast(command)
        revision += 1
        lastCommandAt = 0L
        return command.apply(state)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastCommandAt = 0L
        revision += 1
    }

    fun state(): HistoryState = HistoryState(
        canUndo = canUndo,
        canRedo = canRedo,
        undoLabel = undoStack.lastOrNull()?.label,
        redoLabel = redoStack.lastOrNull()?.label,
        entries = undoStack.map { HistoryEntry(it.label, lastCommandAt) },
        revision = revision,
    )
}

data class CommandResult<S>(
    val state: S,
    val applied: Boolean,
    val rejection: String?,
)
