package com.cartoonstudio.core.undo

/**
 * Every user mutation in the editor follows:
 *
 * `Intent -> Command -> Validate -> Apply -> Record -> Notify`
 *
 * A command is a pure function over the document state: [apply] returns the new
 * state and [revert] restores the previous one. Keeping commands pure makes
 * undo/redo exact and lets the autosave layer snapshot safely at any point.
 */
interface Command<S> {

    /** Short human readable description, shown in the history panel. */
    val label: String

    /**
     * Commands with the same non-null merge key that arrive inside
     * [mergeWindowMillis] collapse into a single history entry — this is what
     * makes dragging a slider produce one undo step instead of hundreds.
     */
    val mergeKey: String? get() = null

    val mergeWindowMillis: Long get() = 700L

    /** Returns null when the command cannot be applied to [state]. */
    fun validate(state: S): String? = null

    fun apply(state: S): S

    fun revert(state: S): S
}

/**
 * Generic command that stores the before/after documents.
 *
 * Suitable for the vast majority of editor operations: the document model is
 * an immutable tree of small data classes, so structural sharing keeps the
 * memory cost of a snapshot proportional to what actually changed.
 */
class SnapshotCommand<S>(
    override val label: String,
    private val before: S,
    private val after: S,
    override val mergeKey: String? = null,
) : Command<S> {
    override fun apply(state: S): S = after
    override fun revert(state: S): S = before

    internal fun mergedWith(next: SnapshotCommand<S>): SnapshotCommand<S> =
        SnapshotCommand(next.label, before, next.after, mergeKey)
}
