package com.cartoonstudio.engine.export

import com.cartoonstudio.domain.export.ExportScope
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.ProjectFactory
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.ProjectTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportPlannerTest {

    private val project = ProjectFactory.createProject(
        name = "Test",
        settings = ProjectSettings.HD,
        template = ProjectTemplate.Storyboard,
        nowMillis = 0L,
    )

    @Test
    fun `whole project plans every frame of every shot`() {
        val plan = ExportPlanner.plan(project, ExportSettings())
        assertEquals(project.scenes.sumOf { it.durationFrames }, plan.frameCount)
        assertEquals(1920, plan.widthPixels)
        assertEquals(1080, plan.heightPixels)
    }

    @Test
    fun `single scene scope only plans that shot`() {
        val scene = project.scenes[2]
        val plan = ExportPlanner.plan(
            project,
            ExportSettings(scope = ExportScope.SingleScene(scene.id)),
        )
        assertEquals(scene.durationFrames, plan.frameCount)
        assertTrue(plan.frames.all { it.scene.id == scene.id })
    }

    @Test
    fun `shooting on twos halves the frame count`() {
        val full = ExportPlanner.plan(project, ExportSettings())
        val twos = ExportPlanner.plan(project, ExportSettings(frameStep = 2))
        assertEquals((full.frameCount + 1) / 2, twos.frameCount)
    }

    @Test
    fun `resolution scale produces even encoder friendly dimensions`() {
        val plan = ExportPlanner.plan(project, ExportSettings(resolutionScale = 0.333f))
        assertEquals(0, plan.widthPixels % 2)
        assertEquals(0, plan.heightPixels % 2)
    }

    @Test
    fun `an unknown scene id plans nothing`() {
        val plan = ExportPlanner.plan(project, ExportSettings(scope = ExportScope.SingleScene("nope")))
        assertTrue(plan.isEmpty)
    }
}
