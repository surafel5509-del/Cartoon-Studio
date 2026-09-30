package com.cartoonstudio.data.assetstore

import com.cartoonstudio.domain.model.AssetCategory
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetOrigin
import com.cartoonstudio.domain.model.ContentPack
import com.cartoonstudio.domain.model.ParticlePreset
import com.cartoonstudio.domain.camera.CameraPreset
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Palette
import com.cartoonstudio.domain.rigging.Skeleton

/**
 * The complete built-in catalog.
 *
 * Only lightweight descriptors are built eagerly; payloads (artwork, rigs,
 * clips) are resolved on demand, which is what lets the browser stay smooth
 * with very large libraries.
 */
object AssetCatalog {

    private fun descriptor(
        id: String,
        name: String,
        category: AssetCategory,
        tags: List<String>,
        packId: String,
        description: String = "",
        rigProfile: String? = null,
    ) = AssetDescriptor(
        id = id,
        name = name,
        category = category,
        origin = AssetOrigin.BuiltIn,
        tags = tags,
        packId = packId,
        rigProfile = rigProfile,
        description = description.ifBlank { "$name — ${category.displayName}" },
    )

    private val audioDescriptors: List<AssetDescriptor> = buildList {
        val sfx = listOf(
            "Pop", "Whoosh", "Boing", "Thud", "Splash", "Click", "Ding", "Crash",
            "Footstep", "Door Open", "Paper Rustle", "Bell", "Zap", "Sparkle", "Slide Whistle",
        )
        sfx.forEach {
            add(
                descriptor(
                    "sfx_${it.lowercase().replace(' ', '_')}", it, AssetCategory.SoundEffect,
                    listOf("sfx", "cartoon", it.lowercase()), BuiltInPacks.AUDIO,
                )
            )
        }
        listOf("Happy Loop", "Adventure Theme", "Gentle Piano", "Chase", "Mystery", "Credits").forEach {
            add(
                descriptor(
                    "music_${it.lowercase().replace(' ', '_')}", it, AssetCategory.Music,
                    listOf("music", "loop"), BuiltInPacks.AUDIO,
                )
            )
        }
        listOf("Forest Day", "City Traffic", "Rain", "Ocean Waves", "Room Tone", "Wind").forEach {
            add(
                descriptor(
                    "amb_${it.lowercase().replace(' ', '_')}", it, AssetCategory.Ambience,
                    listOf("ambience", "background"), BuiltInPacks.AUDIO,
                )
            )
        }
    }

    private val effectDescriptors: List<AssetDescriptor> = buildList {
        ParticlePreset.entries.forEach { preset ->
            add(
                descriptor(
                    "particle_${preset.name.lowercase()}", preset.displayName, AssetCategory.Particle,
                    listOf("particles", "vfx", preset.name.lowercase()), BuiltInPacks.EFFECTS,
                    "Deterministic ${preset.displayName.lowercase()} emitter",
                )
            )
        }
        listOf(
            "Impact Flash", "Speed Lines", "Dust Puff", "Shockwave", "Water Splash",
            "Electric Arc", "Smoke Trail", "Star Burst",
        ).forEach {
            add(
                descriptor(
                    "vfx_${it.lowercase().replace(' ', '_')}", it, AssetCategory.Vfx,
                    listOf("vfx", "effect"), BuiltInPacks.EFFECTS,
                )
            )
        }
        listOf("Paper", "Canvas", "Halftone", "Watercolor", "Noise", "Wood", "Concrete").forEach {
            add(
                descriptor(
                    "tex_${it.lowercase()}", it, AssetCategory.Texture,
                    listOf("texture", "surface"), BuiltInPacks.EFFECTS,
                )
            )
        }
        Brush.builtIn.forEach {
            add(
                descriptor(
                    "brushasset_${it.id}", it.name, AssetCategory.BrushPack,
                    listOf("brush", "drawing"), BuiltInPacks.EFFECTS,
                )
            )
        }
        Palette.builtIn.forEach {
            add(
                descriptor(
                    "paletteasset_${it.id}", it.name, AssetCategory.PalettePack,
                    listOf("palette", "color"), BuiltInPacks.EFFECTS,
                )
            )
        }
    }

    private val productionDescriptors: List<AssetDescriptor> = buildList {
        CameraPreset.builtIn.forEach {
            add(
                descriptor(
                    "campreset_${it.id}", it.name, AssetCategory.CameraPreset,
                    listOf("camera", "shot"), BuiltInPacks.PRODUCTION, it.description,
                )
            )
        }
        listOf(
            "Squash and Stretch", "Anticipation", "Overshoot", "Slow In Slow Out",
            "Shake", "Pop In", "Fade In", "Slide In", "Bounce In", "Spin",
        ).forEach {
            add(
                descriptor(
                    "motionpreset_${it.lowercase().replace(' ', '_')}", it, AssetCategory.MotionPreset,
                    listOf("motion", "preset", "animation"), BuiltInPacks.PRODUCTION,
                )
            )
        }
        listOf(
            "Title Card", "Lower Third", "Speech Bubble", "Thought Bubble",
            "Comic Panel", "End Card", "Split Screen",
        ).forEach {
            add(
                descriptor(
                    "template_${it.lowercase().replace(' ', '_')}", it, AssetCategory.Template,
                    listOf("template", "layout"), BuiltInPacks.PRODUCTION,
                )
            )
        }
    }

    private val motionDescriptors: List<AssetDescriptor> by lazy {
        AnimationLibrary.clips.map { clip ->
            descriptor(
                id = "clipasset_${clip.id}",
                name = clip.name,
                category = AssetCategory.CharacterAnimation,
                tags = clip.tags + listOf("animation", "clip", clip.category.displayName.lowercase()),
                packId = BuiltInPacks.MOTION,
                description = "${clip.lengthInFrames} frame ${clip.category.displayName.lowercase()} cycle",
                rigProfile = Skeleton.BIPED_PROFILE,
            )
        }
    }

    private val poseDescriptors: List<AssetDescriptor> by lazy {
        CharacterLibrary.packages.firstOrNull()?.poses.orEmpty().map { pose ->
            descriptor(
                id = "poseasset_${pose.name.lowercase().replace(' ', '_')}",
                name = pose.name,
                category = AssetCategory.PoseLibrary,
                tags = pose.tags + listOf("pose"),
                packId = BuiltInPacks.MOTION,
                rigProfile = Skeleton.BIPED_PROFILE,
            )
        }
    }

    private val expressionDescriptors: List<AssetDescriptor> by lazy {
        CharacterLibrary.packages.firstOrNull()?.expressions.orEmpty().map { expression ->
            descriptor(
                id = "expressionasset_${expression.name.lowercase()}",
                name = expression.name,
                category = AssetCategory.Expression,
                tags = listOf("expression", "face", expression.name.lowercase()),
                packId = BuiltInPacks.MOTION,
                rigProfile = Skeleton.BIPED_PROFILE,
            )
        }
    }

    /** Every built-in descriptor, indexed for search. */
    val descriptors: List<AssetDescriptor> by lazy {
        buildList {
            addAll(CharacterLibrary.packages.map { it.descriptor })
            addAll(motionDescriptors)
            addAll(poseDescriptors)
            addAll(expressionDescriptors)
            addAll(PropLibrary.artwork.map { it.descriptor })
            addAll(effectDescriptors)
            addAll(audioDescriptors)
            addAll(productionDescriptors)
        }
    }

    val packs: List<ContentPack> by lazy {
        listOf(
            ContentPack(BuiltInPacks.CAST, "Core Cast", 1, "Rigged characters", CharacterLibrary.packages.map { it.id }),
            ContentPack(BuiltInPacks.MOTION, "Core Motion", 1, "Reusable animation", motionDescriptors.map { it.id }),
            ContentPack(BuiltInPacks.WORLD, "Core World", 1, "Props and environments", PropLibrary.artwork.map { it.descriptor.id }),
            ContentPack(BuiltInPacks.EFFECTS, "Core Effects", 1, "Particles and textures", effectDescriptors.map { it.id }),
            ContentPack(BuiltInPacks.AUDIO, "Core Audio", 1, "Sound effects and music", audioDescriptors.map { it.id }),
            ContentPack(BuiltInPacks.PRODUCTION, "Production Kit", 1, "Camera and templates", productionDescriptors.map { it.id }),
        )
    }

    val countsByCategory: Map<AssetCategory, Int> by lazy {
        descriptors.groupingBy { it.category }.eachCount()
    }

    val total: Int get() = descriptors.size
}
