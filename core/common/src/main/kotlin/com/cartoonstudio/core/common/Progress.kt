package com.cartoonstudio.core.common

/** Observable state of a long running, cancellable background job. */
sealed interface JobState {
    data object Idle : JobState
    data class Running(val fraction: Float, val label: String) : JobState
    data class Done(val label: String) : JobState
    data class Failed(val error: AppError) : JobState
    data object Cancelled : JobState

    val isActive: Boolean get() = this is Running
}

/** Simple progress reporting callback used by engine and export jobs. */
fun interface ProgressReporter {
    fun report(fraction: Float, label: String)

    companion object {
        val None = ProgressReporter { _, _ -> }
    }
}
