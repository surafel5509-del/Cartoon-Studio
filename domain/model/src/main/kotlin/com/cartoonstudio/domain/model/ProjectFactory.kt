package com.cartoonstudio.domain.model

import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.drawing.Rgba

/** Starting points offered on the "New project" screen. */
enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val sceneCount: Int,
    val durationFrames: Int,
) {
    Blank("Blank Project", "One empty scene, ready to draw", 1, 96),
    ShortFilm("Short Film", "Three shots with backdrop and character layers", 3, 144),
    Explainer("Explainer", "Title, body and outro scenes", 3, 120),
    Storyboard("Storyboard", "Six quick shots for blocking a sequence", 6, 48),
    LoopAnimation("Looping Animation", "A single 24 frame seamless loop", 1, 24),
}

/**
 * Builds new documents.
 *
 * Every created project is immediately valid and renderable: it has a scene, a
 * camera, a backdrop and a drawing layer, so the editor never has to handle an
 * "empty" special case.
 */
object ProjectFactory {

    fun createProject(
        name: String,
        settings: ProjectSettings = ProjectSettings.HD,
        template: ProjectTemplate = ProjectTemplate.Blank,
        nowMillis: Long = System.currentTimeMillis(),
    ): Project {
        val scenes = (0 until template.sceneCount).map { index ->
            createScene(
                name = sceneNameFor(template, index),
                settings = settings,
                shotNumber = index + 1,
                durationFrames = template.durationFrames,
                withCharacterPlaceholder = template == ProjectTemplate.ShortFilm,
                withTitle = template == ProjectTemplate.Explainer && index == 0,
            )
        }
        return Project(
            id = Ids.next("proj"),
            name = name.ifBlank { "Untitled" },
            settings = settings,
            scenes = scenes,
            activeSceneId = scenes.firstOrNull()?.id,
            createdAtMillis = nowMillis,
            modifiedAtMillis = nowMillis,
            revision = 1,
            description = template.description,
        )
    }

    fun createScene(
        name: String,
        settings: ProjectSettings,
        shotNumber: Int = 1,
        durationFrames: Int = 96,
        withCharacterPlaceholder: Boolean = false,
        withTitle: Boolean = false,
    ): Scene {
        val center = Vec2(settings.canvasWidth / 2f, settings.canvasHeight / 2f)
        val layers = buildList {
            if (withTitle) {
                add(
                    Layer(
                        id = Ids.next("layer"),
                        name = "Title",
                        content = LayerContent.Text(
                            text = "Your Title",
                            fontSize = settings.canvasHeight * 0.12f,
                            color = Rgba.Ink,
                        ),
                        transform = Transform2D(position = center),
                    )
                )
            }
            if (withCharacterPlaceholder) {
                add(
                    Layer(
                        id = Ids.next("layer"),
                        name = "Foreground",
                        content = LayerContent.Drawing(),
                        parallax = com.cartoonstudio.domain.camera.ParallaxSettings.Foreground,
                    )
                )
            }
            add(
                Layer(
                    id = Ids.next("layer"),
                    name = "Animation",
                    content = LayerContent.Drawing(),
                    transform = Transform2D(position = Vec2.ZERO),
                )
            )
            add(
                Layer(
                    id = Ids.next("layer"),
                    name = "Background",
                    content = LayerContent.Backdrop(
                        color = Rgba.of(206, 232, 255),
                        gradientEndColor = Rgba.of(250, 245, 230),
                    ),
                    locked = false,
                    parallax = com.cartoonstudio.domain.camera.ParallaxSettings.Background,
                )
            )
        }
        return Scene(
            id = Ids.next("scene"),
            name = name,
            layers = layers,
            camera = Camera(id = Ids.next("camera"), position = center),
            durationFrames = durationFrames,
            backgroundColor = settings.backgroundColor,
            shotNumber = shotNumber,
        )
    }

    fun createDrawingLayer(name: String = "Drawing") =
        Layer(id = Ids.next("layer"), name = name, content = LayerContent.Drawing())

    fun createGroupLayer(name: String = "Group", children: List<Layer> = emptyList()) =
        Layer(id = Ids.next("layer"), name = name, content = LayerContent.Group(children))

    fun createTextLayer(text: String, position: Vec2, fontSize: Float = 64f) = Layer(
        id = Ids.next("layer"),
        name = text.take(18).ifBlank { "Text" },
        content = LayerContent.Text(text = text, fontSize = fontSize),
        transform = Transform2D(position = position),
    )

    fun createParticleLayer(preset: ParticlePreset, position: Vec2) = Layer(
        id = Ids.next("layer"),
        name = preset.displayName,
        content = LayerContent.Particles(
            ParticleEmitter(id = Ids.next("emitter"), preset = preset),
        ),
        transform = Transform2D(position = position),
    )

    fun createBackdropLayer(color: Rgba) = Layer(
        id = Ids.next("layer"),
        name = "Backdrop",
        content = LayerContent.Backdrop(color = color),
    )

    /** Inserts a blank cel at [frame] so the artist can draw a new pose. */
    fun blankCel() = com.cartoonstudio.domain.drawing.Cel(id = Ids.next("cel"))

    fun keyframeFrames(durationFrames: Int, step: Int): List<Frame> =
        (0 until durationFrames step step.coerceAtLeast(1)).map { Frame(it) }

    private fun sceneNameFor(template: ProjectTemplate, index: Int): String = when (template) {
        ProjectTemplate.Explainer -> listOf("Title", "Body", "Outro").getOrElse(index) { "Scene ${index + 1}" }
        ProjectTemplate.Storyboard -> "Shot ${index + 1}"
        else -> if (index == 0) "Scene 1" else "Scene ${index + 1}"
    }
}
