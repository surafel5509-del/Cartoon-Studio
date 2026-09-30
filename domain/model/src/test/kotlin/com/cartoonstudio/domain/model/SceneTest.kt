package com.cartoonstudio.domain.model

import com.cartoonstudio.core.time.Frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneTest {

    private val project = ProjectFactory.createProject(
        name = "Test",
        template = ProjectTemplate.ShortFilm,
        nowMillis = 0L,
    )

    private val scene get() = requireNotNull(project.activeScene)

    @Test
    fun `a new project is immediately renderable`() {
        assertTrue(project.scenes.isNotEmpty())
        assertNotNull(project.activeScene)
        assertTrue(scene.layers.isNotEmpty())
        assertEquals(project.scenes.first().id, project.activeSceneId)
    }

    @Test
    fun `layer index zero is the front-most layer`() {
        val front = Layer.drawing("layer_front", "Front")
        val updated = scene.withLayerAdded(front)
        assertEquals(front.id, updated.layers.first().id)
    }

    @Test
    fun `removing a layer works at any depth`() {
        val child = Layer.drawing("layer_child", "Child")
        val group = ProjectFactory.createGroupLayer("Group", listOf(child))
        val updated = scene.withLayerAdded(group)
        assertNotNull(updated.layer(child.id))

        val pruned = updated.withoutLayer(child.id)
        assertNull(pruned.layer(child.id))
        assertNotNull(pruned.layer(group.id))
    }

    @Test
    fun `solo hides every non-solo layer`() {
        val solo = Layer.drawing("layer_solo", "Solo").copy(solo = true)
        val updated = scene.withLayerAdded(solo)
        assertTrue(updated.hasSolo)
        val renderable = updated.renderableLayers(Frame.ZERO)
        assertEquals(listOf(solo.id), renderable.map { it.id })
    }

    @Test
    fun `layers outside their in and out range do not render`() {
        val limited = Layer.drawing("layer_limited", "Limited")
            .copy(inFrame = Frame(10), outFrame = Frame(20))
        val updated = scene.withLayerAdded(limited)
        assertFalse(limited.existsAt(Frame(9)))
        assertTrue(limited.existsAt(Frame(10)))
        assertFalse(limited.existsAt(Frame(20)))
        assertTrue(updated.renderableLayers(Frame(15)).any { it.id == limited.id })
        assertFalse(updated.renderableLayers(Frame(5)).any { it.id == limited.id })
    }

    @Test
    fun `cels are held until the next exposure`() {
        val drawing = LayerContent.Drawing()
            .withCel(Frame(0), ProjectFactory.blankCel().copy(id = "cel_a"))
            .withCel(Frame(4), ProjectFactory.blankCel().copy(id = "cel_b"))
        assertEquals("cel_a", drawing.celAt(Frame(3))?.id)
        assertEquals("cel_b", drawing.celAt(Frame(4))?.id)
        assertEquals("cel_b", drawing.celAt(Frame(99))?.id)
        assertEquals(listOf(0, 4), drawing.exposedFrames())
    }

    @Test
    fun `reordering scenes preserves every shot`() {
        val ids = project.scenes.map { it.id }
        val moved = project.withSceneMoved(0, 2)
        assertEquals(ids.size, moved.scenes.size)
        assertEquals(ids[0], moved.scenes[2].id)
        assertEquals(ids.toSet(), moved.scenes.map { it.id }.toSet())
    }

    @Test
    fun `touch bumps the revision so autosave can detect changes`() {
        val touched = project.touched(1_234L)
        assertEquals(project.revision + 1, touched.revision)
        assertEquals(1_234L, touched.modifiedAtMillis)
    }
}
