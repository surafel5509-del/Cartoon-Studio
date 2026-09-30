package com.cartoonstudio.domain.model

import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.rigging.Expression
import com.cartoonstudio.domain.rigging.Pose
import com.cartoonstudio.domain.rigging.Skeleton
import kotlinx.serialization.Serializable

/** Top level classification in the production asset library. */
@Serializable
enum class AssetCategory(val displayName: String, val group: AssetGroup) {
    Character("Characters", AssetGroup.Cast),
    CharacterAnimation("Character Animation", AssetGroup.Cast),
    PoseLibrary("Poses", AssetGroup.Cast),
    Expression("Expressions", AssetGroup.Cast),

    Prop("Props", AssetGroup.World),
    Vehicle("Vehicles", AssetGroup.World),
    Building("Buildings", AssetGroup.World),
    Animal("Animals", AssetGroup.World),
    Creature("Creatures", AssetGroup.World),
    Nature("Nature", AssetGroup.World),
    Vegetation("Trees & Plants", AssetGroup.World),
    Terrain("Ground & Terrain", AssetGroup.World),
    Background("Backgrounds", AssetGroup.World),
    Environment("Environments", AssetGroup.World),
    Sky("Sky & Weather", AssetGroup.World),

    Particle("Particles", AssetGroup.Effects),
    Vfx("Visual Effects", AssetGroup.Effects),
    Texture("Textures", AssetGroup.Effects),
    Material("Materials", AssetGroup.Effects),
    BrushPack("Brushes", AssetGroup.Effects),
    PalettePack("Palettes", AssetGroup.Effects),

    SoundEffect("Sound Effects", AssetGroup.Audio),
    Music("Music", AssetGroup.Audio),
    Ambience("Ambience", AssetGroup.Audio),
    Voice("Voice", AssetGroup.Audio),

    CameraPreset("Camera Presets", AssetGroup.Production),
    MotionPreset("Motion Presets", AssetGroup.Production),
    Template("Templates", AssetGroup.Production),
    ProjectTemplate("Project Templates", AssetGroup.Production);

    companion object {
        fun inGroup(group: AssetGroup) = entries.filter { it.group == group }
    }
}

@Serializable
enum class AssetGroup(val displayName: String) {
    Cast("Cast"),
    World("World"),
    Effects("Effects"),
    Audio("Audio"),
    Production("Production"),
}

/** Where an asset came from; drives badges and update behaviour. */
@Serializable
enum class AssetOrigin { BuiltIn, ContentPack, UserCreated, ProjectLocal, Imported }

/**
 * Catalog metadata for one library asset.
 *
 * Descriptors are intentionally lightweight so tens of thousands of them can
 * be indexed, searched and virtualised without loading the payloads.
 */
@Serializable
data class AssetDescriptor(
    val id: String,
    val name: String,
    val category: AssetCategory,
    val origin: AssetOrigin = AssetOrigin.BuiltIn,
    val tags: List<String> = emptyList(),
    val packId: String? = null,
    val version: Int = 1,
    /** Ids this asset needs in order to resolve (e.g. a rig for a clip). */
    val dependencies: List<String> = emptyList(),
    /** Rig profile compatibility for animation and pose assets. */
    val rigProfile: String? = null,
    val thumbnailRef: String? = null,
    val relativePath: String? = null,
    val favorite: Boolean = false,
    val description: String = "",
    val sizeBytes: Long = 0L,
    val createdAtMillis: Long = 0L,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            category.displayName.lowercase().contains(q) ||
            description.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) }
    }

    val isCompatibleWithAnyRig: Boolean get() = rigProfile == null
}

/** Payload for assets that resolve to drawable artwork. */
@Serializable
data class ArtworkAsset(
    val descriptor: AssetDescriptor,
    val cel: Cel,
    val variants: Map<String, Cel> = emptyMap(),
)

/**
 * A modular, reusable character.
 *
 * A package bundles the artwork layers, the rig, the pose/expression sheets
 * and a library of reusable motion — everything needed to drop a performer
 * into a scene and animate them immediately.
 */
@Serializable
data class CharacterPackage(
    val descriptor: AssetDescriptor,
    val skeleton: Skeleton,
    val artworkLayers: List<Layer> = emptyList(),
    val poses: List<Pose> = emptyList(),
    val expressions: List<Expression> = emptyList(),
    val clips: List<AnimationClip> = emptyList(),
    val variants: List<CharacterVariant> = emptyList(),
    val defaultPoseId: String? = null,
) {
    val id: String get() = descriptor.id
    val name: String get() = descriptor.name

    fun clip(clipId: String): AnimationClip? = clips.firstOrNull { it.id == clipId }
    fun pose(poseId: String): Pose? = poses.firstOrNull { it.id == poseId }
    fun expression(expressionId: String): Expression? = expressions.firstOrNull { it.id == expressionId }
}

/** A recolour / costume variation of a character. */
@Serializable
data class CharacterVariant(
    val id: String,
    val name: String,
    /** layer id -> replacement colour, expressed as packed ARGB. */
    val colorOverrides: Map<String, Int> = emptyMap(),
    val hiddenLayerIds: List<String> = emptyList(),
)

/**
 * A versioned bundle of assets that can be installed, updated or removed
 * without breaking projects that reference it.
 */
@Serializable
data class ContentPack(
    val id: String,
    val name: String,
    val version: Int,
    val description: String = "",
    val assetIds: List<String> = emptyList(),
    val builtIn: Boolean = true,
    val installedAtMillis: Long = 0L,
)

/** Sort orders offered by the asset browser. */
enum class AssetSort(val displayName: String) {
    Relevance("Relevance"),
    NameAscending("Name A–Z"),
    NameDescending("Name Z–A"),
    Newest("Newest"),
    Category("Category"),
}

/** A saved query over the catalog. */
data class AssetFilter(
    val query: String = "",
    val categories: Set<AssetCategory> = emptySet(),
    val groups: Set<AssetGroup> = emptySet(),
    val origins: Set<AssetOrigin> = emptySet(),
    val favoritesOnly: Boolean = false,
    val rigProfile: String? = null,
    val sort: AssetSort = AssetSort.Relevance,
) {
    fun accepts(descriptor: AssetDescriptor): Boolean {
        if (!descriptor.matches(query)) return false
        if (categories.isNotEmpty() && descriptor.category !in categories) return false
        if (groups.isNotEmpty() && descriptor.category.group !in groups) return false
        if (origins.isNotEmpty() && descriptor.origin !in origins) return false
        if (favoritesOnly && !descriptor.favorite) return false
        if (rigProfile != null && descriptor.rigProfile != null && descriptor.rigProfile != rigProfile) return false
        return true
    }

    val isActive: Boolean
        get() = query.isNotBlank() || categories.isNotEmpty() || groups.isNotEmpty() ||
            origins.isNotEmpty() || favoritesOnly
}
