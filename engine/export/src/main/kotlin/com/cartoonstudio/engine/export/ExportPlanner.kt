package com.cartoonstudio.engine.export

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRange
import com.cartoonstudio.domain.export.ExportScope
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.domain.model.Scene

/** One frame of work in a render job. */
data class PlannedFrame(
    val scene: Scene,
    val sceneFrame: Frame,
    /** Position in the final output, used for muxing and file naming. */
    val outputIndex: Int,
)

/** The full, deterministic work list for a render. */
data class ExportPlan(
    val frames: List<PlannedFrame>,
    val widthPixels: Int,
    val heightPixels: Int,
    val frameCount: Int,
    val durationMillis: Long,
) {
    val isEmpty: Boolean get() = frames.isEmpty()

    companion object {
        val Empty = ExportPlan(emptyList(), 0, 0, 0, 0L)
    }
}

/**
 * Expands a project plus export settings into an explicit frame list.
 *
 * Planning up front makes progress reporting exact, makes cancellation clean,
 * and means the renderer itself never has to reason about scopes or scene
 * boundaries.
 */
object ExportPlanner {

    fun plan(project: Project, settings: ExportSettings): ExportPlan {
        val step = settings.frameStep.coerceAtLeast(1)
        val frames = ArrayList<PlannedFrame>()

        when (val scope = settings.scope) {
            is ExportScope.WholeProject ->
                project.scenes.forEach { scene -> appendScene(frames, scene, scene.range, step) }

            is ExportScope.SingleScene ->
                project.scene(scope.sceneId)?.let { appendScene(frames, it, it.range, step) }

            is ExportScope.SceneRange ->
                project.scene(scope.sceneId)?.let { appendScene(frames, it, scope.range, step) }
        }

        val width = settings.outputWidth(project.settings)
        val height = settings.outputHeight(project.settings)
        return ExportPlan(
            frames = frames,
            widthPixels = width,
            heightPixels = height,
            frameCount = frames.size,
            durationMillis = settings.frameRate.millisForFrames(frames.size),
        )
    }

    private fun appendScene(
        target: MutableList<PlannedFrame>,
        scene: Scene,
        range: FrameRange,
        step: Int,
    ) {
        var index = range.start.index
        while (index < range.endExclusive.index) {
            target += PlannedFrame(scene, Frame(index), target.size)
            index += step
        }
    }

    /** File name for a frame in a PNG sequence export. */
    fun frameFileName(stem: String, index: Int, total: Int): String {
        val digits = maxOf(4, total.toString().length)
        return "${stem}_${index.toString().padStart(digits, '0')}.png"
    }
}
