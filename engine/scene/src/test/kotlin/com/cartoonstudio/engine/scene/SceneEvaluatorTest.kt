package com.cartoonstudio.engine.scene

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.animation.PropertyTracks
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.ProjectFactory
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.ProjectTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneEvaluatorTest {

    private val evaluator = SceneEvaluator()

    private val options = EvaluationOptions(viewportWidth = 1920f, viewportHeight = 1080f)

    private val project = ProjectFactory.createProject(
        name = "Test",
        settings = ProjectSettings.HD,
        template = ProjectTemplate.Blank,
        nowMillis = 0L,
    )

    private val scene get() = requireNotNull(project.activeScene)

    @Test
    fun `evaluation returns layers back to front`() {
        val front = Layer.drawing("layer_front", "Front")
        val back = Layer.drawing("layer_back", "Back")
        val prepared = scene.copy(layers = listOf(front, back))

        val evaluated = evaluator.evaluate(prepared, Frame.ZERO, options)
        val ids = evaluated.layers.map { it.layerId }
        assertTrue(ids.indexOf("layer_back") < ids.indexOf("layer_front"))
    }

    @Test
    fun `animated transforms are sampled at the requested frame`() {
        val animated = Layer.drawing("layer_anim", "Animated").copy(
            tracks = PropertyTracks()
                .withKey(PropertyPath.POSITION_X, Frame(0), 0f, Interpolation.Linear)
                .withKey(PropertyPath.POSITION_X, Frame(10), 100f, Interpolation.Linear),
        )
        val prepared = scene.copy(layers = listOf(animated))

        val atStart = evaluator.evaluate(prepared, Frame(0), options).layer("layer_anim")
        val atMiddle = evaluator.evaluate(prepared, Frame(5), options).layer("layer_anim")
        assertNotNull(atStart)
        assertNotNull(atMiddle)
        assertTrue(atMiddle.worldMatrix.tx > atStart.worldMatrix.tx)
    }

    @Test
    fun `opacity multiplies down the group hierarchy`() {
        val child = Layer.drawing("layer_child", "Child").copy(opacity = 0.5f)
        val group = Layer.group("layer_group", "Group", listOf(child)).copy(opacity = 0.5f)
        val prepared = scene.copy(layers = listOf(group))

        val evaluated = evaluator.evaluate(prepared, Frame.ZERO, options)
        val resolved = evaluated.layer("layer_child")
        assertNotNull(resolved)
        assertEquals(0.25f, resolved.opacity, absoluteTolerance = 0.001f)
    }

    @Test
    fun `hidden layers are skipped`() {
        val hidden = Layer.drawing("layer_hidden", "Hidden").copy(visible = false)
        val prepared = scene.copy(layers = listOf(hidden))
        val evaluated = evaluator.evaluate(prepared, Frame.ZERO, options)
        assertEquals(0, evaluated.layers.count { it.layerId == "layer_hidden" })
    }

    @Test
    fun `evaluating the same frame twice gives identical results`() {
        val first = evaluator.evaluate(scene, Frame(3), options)
        val second = evaluator.evaluate(scene, Frame(3), options)
        assertEquals(first.layers.map { it.layerId }, second.layers.map { it.layerId })
        assertEquals(first.contentBounds, second.contentBounds)
        assertEquals(first.viewMatrix, second.viewMatrix)
    }
}
