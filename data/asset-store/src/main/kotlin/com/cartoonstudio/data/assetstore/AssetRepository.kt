package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.ClipInstance
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.PropertyTracks
import com.cartoonstudio.domain.animation.Track
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.model.ArtworkAsset
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetFilter
import com.cartoonstudio.domain.model.AssetSort
import com.cartoonstudio.domain.model.CharacterPackage
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.Transform2D
import com.cartoonstudio.domain.rigging.Pose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A page of catalog results, for virtualised browsing of large libraries. */
data class AssetPage(
    val items: List<AssetDescriptor>,
    val totalMatches: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + items.size < totalMatches
}

/**
 * Searchable index over every available asset.
 *
 * The index is built once from the built-in catalog plus any user-created or
 * project-local assets, then queried with bounded pages so the UI never holds
 * the entire library in memory.
 */
class AssetRepository {

    private val userAssets = MutableStateFlow<List<AssetDescriptor>>(emptyList())
    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    private val recents = MutableStateFlow<List<String>>(emptyList())

    val favoriteIds: StateFlow<Set<String>> = favorites.asStateFlow()
    val recentIds: StateFlow<List<String>> = recents.asStateFlow()

    private val index: List<AssetDescriptor> get() = AssetCatalog.descriptors + userAssets.value

    fun all(): List<AssetDescriptor> = index

    fun descriptor(assetId: String): AssetDescriptor? = index.firstOrNull { it.id == assetId }

    fun query(filter: AssetFilter, offset: Int = 0, limit: Int = 60): AssetPage {
        val favoriteSet = favorites.value
        val matches = index
            .map { if (it.id in favoriteSet) it.copy(favorite = true) else it }
            .filter(filter::accepts)
        val sorted = when (filter.sort) {
            AssetSort.NameAscending -> matches.sortedBy { it.name.lowercase() }
            AssetSort.NameDescending -> matches.sortedByDescending { it.name.lowercase() }
            AssetSort.Newest -> matches.sortedByDescending { it.createdAtMillis }
            AssetSort.Category -> matches.sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))
            AssetSort.Relevance -> matches.sortedWith(
                compareByDescending<AssetDescriptor> { relevance(it, filter.query) }.thenBy { it.name.lowercase() }
            )
        }
        val page = sorted.drop(offset).take(limit)
        return AssetPage(page, sorted.size, offset)
    }

    private fun relevance(descriptor: AssetDescriptor, query: String): Int {
        if (query.isBlank()) return if (descriptor.favorite) 1 else 0
        val q = query.trim().lowercase()
        val name = descriptor.name.lowercase()
        return when {
            name == q -> 100
            name.startsWith(q) -> 80
            name.contains(q) -> 60
            descriptor.tags.any { it.lowercase() == q } -> 50
            descriptor.tags.any { it.lowercase().contains(q) } -> 30
            else -> 10
        } + if (descriptor.favorite) 5 else 0
    }

    fun toggleFavorite(assetId: String) {
        favorites.value = favorites.value.toMutableSet().apply {
            if (!add(assetId)) remove(assetId)
        }
    }

    fun setFavorites(ids: Set<String>) {
        favorites.value = ids
    }

    fun markUsed(assetId: String) {
        recents.value = (listOf(assetId) + recents.value.filterNot { it == assetId }).take(MAX_RECENTS)
    }

    fun registerUserAsset(descriptor: AssetDescriptor) {
        userAssets.value = userAssets.value + descriptor
    }

    fun character(packageId: String): CharacterPackage? = CharacterLibrary.byId(packageId)

    fun artwork(assetId: String): ArtworkAsset? = PropLibrary.resolve(assetId)

    fun clip(clipId: String): AnimationClip? = AnimationLibrary.resolve(clipId)

    /** Resolves a clip from either its own id or its catalog descriptor id. */
    fun clipForDescriptor(descriptorId: String): AnimationClip? =
        AnimationLibrary.resolve(descriptorId.removePrefix("clipasset_"))

    private companion object {
        const val MAX_RECENTS = 40
    }
}

/**
 * Turns library content into scene layers.
 *
 * Characters are instanced as a group of artwork layers whose ids map onto rig
 * bones, so applying a reusable clip is a matter of routing each bone channel
 * onto the matching layer — this is the concrete form of retargeting.
 */
object AssetInstancer {

    fun instantiateCharacter(
        pack: CharacterPackage,
        position: Vec2,
        scale: Float = 1f,
    ): Layer {
        val instanceId = Ids.next("layer")
        val children = pack.artworkLayers.map { part ->
            part.copy(id = "${instanceId}__${part.id}")
        }
        return Layer(
            id = instanceId,
            name = pack.name,
            content = LayerContent.Group(children = children),
            transform = Transform2D(position = position, scale = Vec2(scale, scale)),
        )
    }

    fun instantiateArtwork(asset: ArtworkAsset, position: Vec2, scale: Float = 1f): Layer = Layer(
        id = Ids.next("layer"),
        name = asset.descriptor.name,
        content = LayerContent.Drawing(cels = mapOf(0 to asset.cel.copy(id = Ids.next("cel")))),
        transform = Transform2D(position = position, scale = Vec2(scale, scale)),
    )

    /** Maps a character instance's child layer back to the bone that drives it. */
    fun boneIdForChild(child: Layer): String? =
        child.id.substringAfterLast("__").takeIf { it.startsWith("art_") }?.removePrefix("art_")

    /**
     * Bakes a reusable clip onto a character instance.
     *
     * Bone rotation channels become transform keys on the matching artwork
     * layer, and the clip's root channels drive the group itself.
     */
    fun applyClip(
        characterLayer: Layer,
        clip: AnimationClip,
        startFrame: Frame,
        loopCount: Int = 1,
    ): Layer {
        val group = characterLayer.content as? LayerContent.Group ?: return characterLayer
        val repeats = loopCount.coerceAtLeast(1)

        val children = group.children.map { child ->
            val boneId = boneIdForChild(child) ?: return@map child
            val boneTracks = clip.boneTracks[boneId] ?: return@map child
            var tracks = child.tracks
            for ((property, track) in boneTracks.tracks) {
                var merged = tracks.track(property) ?: Track(property)
                for (repeat in 0 until repeats) {
                    val offset = startFrame.index + repeat * clip.lengthInFrames
                    track.keys.forEach { key ->
                        merged = merged.withKey(key.copy(frame = Frame(key.frame.index + offset)))
                    }
                }
                tracks = tracks.withTrack(merged)
            }
            child.copy(tracks = tracks)
        }

        var rootTracks = characterLayer.tracks
        for ((property, track) in clip.tracks.tracks) {
            var merged = rootTracks.track(property) ?: Track(property)
            for (repeat in 0 until repeats) {
                val offset = startFrame.index + repeat * clip.lengthInFrames
                track.keys.forEach { key ->
                    merged = merged.withKey(key.copy(frame = Frame(key.frame.index + offset)))
                }
            }
            rootTracks = rootTracks.withTrack(merged)
        }

        return characterLayer.copy(
            content = group.copy(children = children),
            tracks = rootTracks,
        )
    }

    /** Applies a saved pose as a single keyframe set at [frame]. */
    fun applyPose(characterLayer: Layer, pose: Pose, frame: Frame): Layer {
        val group = characterLayer.content as? LayerContent.Group ?: return characterLayer
        val children = group.children.map { child ->
            val boneId = boneIdForChild(child) ?: return@map child
            val values = pose.boneValues[boneId] ?: return@map child
            var tracks: PropertyTracks = child.tracks
            values.forEach { (property, value) ->
                tracks = tracks.withKey(property, frame, value, Interpolation.EaseInOut)
            }
            child.copy(tracks = tracks)
        }
        return characterLayer.copy(content = group.copy(children = children))
    }

    /** Creates a clip instance reference for the non-destructive motion stack. */
    fun clipInstance(clip: AnimationClip, startFrame: Frame, loopCount: Int = 1) = ClipInstance(
        id = Ids.next("clipinst"),
        clipId = clip.id,
        startFrame = startFrame,
        loopCount = loopCount,
    )
}
