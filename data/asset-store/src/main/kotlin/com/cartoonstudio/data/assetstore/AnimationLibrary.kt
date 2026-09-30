package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.ClipCategory
import com.cartoonstudio.domain.animation.ExtrapolationMode
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.Keyframe
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.animation.PropertyTracks
import com.cartoonstudio.domain.animation.Track
import com.cartoonstudio.domain.rigging.Skeleton

/** Small builder that keeps the clip definitions below readable. */
private class ClipBuilder(val length: Int) {
    val boneTracks = LinkedHashMap<String, MutableMap<String, Track>>()
    val rootTracks = LinkedHashMap<String, Track>()

    /** Adds rotation keys for a bone as `frame to degrees` pairs. */
    fun bone(boneId: String, vararg keys: Pair<Int, Float>, property: String = PropertyPath.ROTATION) {
        val channels = boneTracks.getOrPut(boneId) { LinkedHashMap() }
        val existing = channels[property] ?: Track(property, preExtrapolation = ExtrapolationMode.Loop, postExtrapolation = ExtrapolationMode.Loop)
        channels[property] = keys.fold(existing) { track, (frame, value) ->
            track.withKey(Keyframe(Frame(frame), value, Interpolation.EaseInOut))
        }
    }

    fun root(property: String, vararg keys: Pair<Int, Float>) {
        val existing = rootTracks[property] ?: Track(property)
        rootTracks[property] = keys.fold(existing) { track, (frame, value) ->
            track.withKey(Keyframe(Frame(frame), value, Interpolation.EaseInOut))
        }
    }

    fun build(
        id: String,
        name: String,
        category: ClipCategory,
        loopable: Boolean,
        tags: List<String>,
    ) = AnimationClip(
        id = id,
        name = name,
        category = category,
        tracks = PropertyTracks(rootTracks.toMap()),
        boneTracks = boneTracks.mapValues { (_, channels) -> PropertyTracks(channels.toMap()) },
        lengthInFrames = length,
        loopable = loopable,
        tags = tags,
        builtIn = true,
        rigProfile = Skeleton.BIPED_PROFILE,
    )
}

private fun clip(
    id: String,
    name: String,
    category: ClipCategory,
    length: Int,
    loopable: Boolean = true,
    tags: List<String> = emptyList(),
    block: ClipBuilder.() -> Unit,
): AnimationClip = ClipBuilder(length).apply(block).build(id, name, category, loopable, tags)

/**
 * The reusable motion library.
 *
 * Every clip is authored against the shared `biped.v1` rig profile, so all of
 * them apply to every built-in character and to any user rig that uses the
 * same bone names.
 */
object AnimationLibrary {

    private val B = BipedBones

    val idle = clip("clip_idle", "Idle", ClipCategory.Idle, 48, tags = listOf("breathe", "stand")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 24 to 4f, 48 to 0f)
        bone(B.CHEST, 0 to 0f, 24 to 2.5f, 48 to 0f)
        bone(B.HEAD, 0 to 0f, 16 to -1.5f, 32 to 1.5f, 48 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 24 to 3f, 48 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 24 to -3f, 48 to 0f)
    }

    val walk = clip("clip_walk", "Walk", ClipCategory.Locomotion, 24, tags = listOf("cycle", "move")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 6 to -5f, 12 to 0f, 18 to -5f, 24 to 0f)
        bone(B.LEG_L_UPPER, 0 to 25f, 6 to 0f, 12 to -25f, 18 to 0f, 24 to 25f)
        bone(B.LEG_R_UPPER, 0 to -25f, 6 to 0f, 12 to 25f, 18 to 0f, 24 to -25f)
        bone(B.LEG_L_LOWER, 0 to -5f, 6 to -22f, 12 to 0f, 18 to -8f, 24 to -5f)
        bone(B.LEG_R_LOWER, 0 to 0f, 6 to -8f, 12 to -5f, 18 to -22f, 24 to 0f)
        bone(B.ARM_L_UPPER, 0 to -22f, 12 to 22f, 24 to -22f)
        bone(B.ARM_R_UPPER, 0 to 22f, 12 to -22f, 24 to 22f)
        bone(B.CHEST, 0 to 1f, 12 to -1f, 24 to 1f)
    }

    val run = clip("clip_run", "Run", ClipCategory.Locomotion, 16, tags = listOf("cycle", "fast")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 4 to -14f, 8 to 0f, 12 to -14f, 16 to 0f)
        bone(B.LEG_L_UPPER, 0 to 45f, 8 to -45f, 16 to 45f)
        bone(B.LEG_R_UPPER, 0 to -45f, 8 to 45f, 16 to -45f)
        bone(B.LEG_L_LOWER, 0 to -20f, 4 to -70f, 8 to -5f, 16 to -20f)
        bone(B.LEG_R_LOWER, 0 to -5f, 8 to -20f, 12 to -70f, 16 to -5f)
        bone(B.ARM_L_UPPER, 0 to -60f, 8 to 45f, 16 to -60f)
        bone(B.ARM_R_UPPER, 0 to 60f, 8 to -45f, 16 to 60f)
        bone(B.ARM_L_LOWER, 0 to -60f, 8 to -35f, 16 to -60f)
        bone(B.ARM_R_LOWER, 0 to 60f, 8 to 35f, 16 to 60f)
        bone(B.CHEST, 0 to 8f, 16 to 8f)
    }

    val jump = clip("clip_jump", "Jump", ClipCategory.Action, 28, loopable = false, tags = listOf("air")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 6 to 18f, 12 to -120f, 20 to -120f, 26 to 14f, 28 to 0f)
        bone(B.LEG_L_UPPER, 0 to 0f, 6 to 45f, 14 to -20f, 26 to 45f, 28 to 0f)
        bone(B.LEG_R_UPPER, 0 to 0f, 6 to 45f, 14 to -20f, 26 to 45f, 28 to 0f)
        bone(B.LEG_L_LOWER, 0 to 0f, 6 to -60f, 14 to -10f, 26 to -60f, 28 to 0f)
        bone(B.LEG_R_LOWER, 0 to 0f, 6 to -60f, 14 to -10f, 26 to -60f, 28 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 8 to -140f, 20 to -120f, 28 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 8 to 140f, 20 to 120f, 28 to 0f)
    }

    val fall = clip("clip_fall", "Fall", ClipCategory.Action, 20, tags = listOf("air")) {
        bone(B.ARM_L_UPPER, 0 to -120f, 10 to -150f, 20 to -120f)
        bone(B.ARM_R_UPPER, 0 to 120f, 10 to 150f, 20 to 120f)
        bone(B.LEG_L_UPPER, 0 to 15f, 10 to 30f, 20 to 15f)
        bone(B.LEG_R_UPPER, 0 to -15f, 10 to -30f, 20 to -15f)
        bone(B.CHEST, 0 to -6f, 20 to -6f)
    }

    val land = clip("clip_land", "Land", ClipCategory.Action, 16, loopable = false, tags = listOf("impact")) {
        root(PropertyPath.POSITION_Y, 0 to -40f, 4 to 22f, 10 to -4f, 16 to 0f)
        bone(B.LEG_L_UPPER, 0 to -20f, 4 to 55f, 16 to 0f)
        bone(B.LEG_R_UPPER, 0 to 20f, 4 to 55f, 16 to 0f)
        bone(B.LEG_L_LOWER, 0 to 0f, 4 to -70f, 16 to 0f)
        bone(B.LEG_R_LOWER, 0 to 0f, 4 to -70f, 16 to 0f)
        bone(B.CHEST, 0 to 0f, 4 to 14f, 16 to 0f)
    }

    val crouch = clip("clip_crouch", "Crouch", ClipCategory.Idle, 20, loopable = false, tags = listOf("low")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 12 to 45f, 20 to 45f)
        bone(B.LEG_L_UPPER, 0 to 0f, 12 to 70f, 20 to 70f)
        bone(B.LEG_R_UPPER, 0 to 0f, 12 to 70f, 20 to 70f)
        bone(B.LEG_L_LOWER, 0 to 0f, 12 to -80f, 20 to -80f)
        bone(B.LEG_R_LOWER, 0 to 0f, 12 to -80f, 20 to -80f)
        bone(B.CHEST, 0 to 0f, 12 to 16f, 20 to 16f)
    }

    val sit = clip("clip_sit", "Sit", ClipCategory.Idle, 24, loopable = false, tags = listOf("rest")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 16 to 70f, 24 to 70f)
        bone(B.LEG_L_UPPER, 0 to 0f, 16 to 88f, 24 to 88f)
        bone(B.LEG_R_UPPER, 0 to 0f, 16 to 88f, 24 to 88f)
        bone(B.LEG_L_LOWER, 0 to 0f, 16 to -88f, 24 to -88f)
        bone(B.LEG_R_LOWER, 0 to 0f, 16 to -88f, 24 to -88f)
    }

    val stand = clip("clip_stand", "Stand Up", ClipCategory.Action, 24, loopable = false, tags = listOf("rise")) {
        root(PropertyPath.POSITION_Y, 0 to 70f, 24 to 0f)
        bone(B.LEG_L_UPPER, 0 to 88f, 24 to 0f)
        bone(B.LEG_R_UPPER, 0 to 88f, 24 to 0f)
        bone(B.LEG_L_LOWER, 0 to -88f, 24 to 0f)
        bone(B.LEG_R_LOWER, 0 to -88f, 24 to 0f)
    }

    val turn = clip("clip_turn", "Turn", ClipCategory.Locomotion, 18, loopable = false, tags = listOf("pivot")) {
        root(PropertyPath.SCALE_X, 0 to 1f, 8 to 0.1f, 9 to -0.1f, 18 to -1f)
        bone(B.CHEST, 0 to 0f, 9 to 8f, 18 to 0f)
        bone(B.HEAD, 0 to 0f, 6 to -10f, 18 to 0f)
    }

    val wave = clip("clip_wave", "Wave", ClipCategory.Gesture, 32, tags = listOf("greeting", "hello")) {
        bone(B.ARM_R_UPPER, 0 to 0f, 8 to -125f, 28 to -125f, 32 to 0f)
        bone(B.ARM_R_LOWER, 0 to 0f, 10 to -20f, 15 to -55f, 20 to -20f, 25 to -55f, 30 to -25f, 32 to 0f)
        bone(B.HEAD, 0 to 0f, 16 to 4f, 32 to 0f)
    }

    val point = clip("clip_point", "Point", ClipCategory.Gesture, 24, loopable = false, tags = listOf("explain")) {
        bone(B.ARM_R_UPPER, 0 to 0f, 8 to -80f, 20 to -80f, 24 to 0f)
        bone(B.ARM_R_LOWER, 0 to 0f, 8 to 12f, 20 to 12f, 24 to 0f)
        bone(B.HEAD, 0 to 0f, 8 to 6f, 24 to 0f)
    }

    val talk = clip("clip_talk", "Talk", ClipCategory.Facial, 24, tags = listOf("dialogue", "speak")) {
        bone(B.HEAD, 0 to 0f, 4 to 3f, 8 to -2f, 12 to 4f, 16 to -3f, 20 to 2f, 24 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 8 to -22f, 16 to -8f, 24 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 12 to 18f, 24 to 0f)
        bone(B.CHEST, 0 to 0f, 12 to 2f, 24 to 0f)
    }

    val laugh = clip("clip_laugh", "Laugh", ClipCategory.Emotion, 24, tags = listOf("happy")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 6 to -8f, 12 to 0f, 18 to -8f, 24 to 0f)
        bone(B.CHEST, 0 to 0f, 6 to -12f, 12 to 0f, 18 to -12f, 24 to 0f)
        bone(B.HEAD, 0 to 0f, 6 to -18f, 12 to -6f, 18 to -18f, 24 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 12 to 28f, 24 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 12 to -28f, 24 to 0f)
    }

    val cry = clip("clip_cry", "Cry", ClipCategory.Emotion, 36, tags = listOf("sad")) {
        bone(B.HEAD, 0 to 12f, 18 to 18f, 36 to 12f)
        bone(B.CHEST, 0 to 10f, 18 to 16f, 36 to 10f)
        bone(B.ARM_L_UPPER, 0 to -110f, 18 to -120f, 36 to -110f)
        bone(B.ARM_R_UPPER, 0 to 110f, 18 to 120f, 36 to 110f)
    }

    val angry = clip("clip_angry", "Angry", ClipCategory.Emotion, 20, tags = listOf("mad")) {
        bone(B.CHEST, 0 to 0f, 5 to -8f, 10 to 0f, 15 to -8f, 20 to 0f)
        bone(B.HEAD, 0 to -6f, 10 to -10f, 20 to -6f)
        bone(B.ARM_L_UPPER, 0 to -30f, 10 to -40f, 20 to -30f)
        bone(B.ARM_R_UPPER, 0 to 30f, 10 to 40f, 20 to 30f)
    }

    val surprised = clip("clip_surprised", "Surprised", ClipCategory.Reaction, 18, loopable = false, tags = listOf("shock")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 4 to -24f, 10 to 0f, 18 to 0f)
        root(PropertyPath.SCALE_Y, 0 to 1f, 4 to 1.12f, 10 to 0.97f, 18 to 1f)
        bone(B.ARM_L_UPPER, 0 to 0f, 4 to -95f, 18 to -60f)
        bone(B.ARM_R_UPPER, 0 to 0f, 4 to 95f, 18 to 60f)
        bone(B.HEAD, 0 to 0f, 4 to -12f, 18 to -4f)
    }

    val scared = clip("clip_scared", "Scared", ClipCategory.Reaction, 24, tags = listOf("fear", "shake")) {
        root(PropertyPath.POSITION_X, 0 to 0f, 2 to -4f, 4 to 4f, 6 to -3f, 8 to 3f, 12 to 0f, 24 to 0f)
        bone(B.CHEST, 0 to 12f, 12 to 16f, 24 to 12f)
        bone(B.ARM_L_UPPER, 0 to -60f, 12 to -70f, 24 to -60f)
        bone(B.ARM_R_UPPER, 0 to 60f, 12 to 70f, 24 to 60f)
        bone(B.HEAD, 0 to 8f, 12 to 12f, 24 to 8f)
    }

    val celebrate = clip("clip_celebrate", "Celebrate", ClipCategory.Emotion, 32, tags = listOf("win", "cheer")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 8 to -60f, 16 to 0f, 24 to -40f, 32 to 0f)
        bone(B.ARM_L_UPPER, 0 to -40f, 8 to -165f, 16 to -40f, 24 to -165f, 32 to -40f)
        bone(B.ARM_R_UPPER, 0 to 40f, 8 to 165f, 16 to 40f, 24 to 165f, 32 to 40f)
        bone(B.LEG_L_UPPER, 0 to 0f, 8 to 30f, 16 to 0f, 24 to 30f, 32 to 0f)
        bone(B.LEG_R_UPPER, 0 to 0f, 8 to -30f, 16 to 0f, 24 to -30f, 32 to 0f)
    }

    val hurt = clip("clip_hurt", "Hurt", ClipCategory.Reaction, 20, loopable = false, tags = listOf("damage")) {
        root(PropertyPath.POSITION_X, 0 to 0f, 4 to 26f, 12 to 8f, 20 to 0f)
        bone(B.CHEST, 0 to 0f, 4 to 22f, 20 to 0f)
        bone(B.HEAD, 0 to 0f, 4 to 26f, 20 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 4 to -45f, 20 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 4 to 20f, 20 to 0f)
    }

    val push = clip("clip_push", "Push", ClipCategory.Action, 28, tags = listOf("effort")) {
        bone(B.CHEST, 0 to 8f, 14 to 16f, 28 to 8f)
        bone(B.ARM_L_UPPER, 0 to -75f, 14 to -85f, 28 to -75f)
        bone(B.ARM_R_UPPER, 0 to 75f, 14 to 85f, 28 to 75f)
        bone(B.LEG_L_UPPER, 0 to 18f, 14 to 26f, 28 to 18f)
        bone(B.LEG_R_UPPER, 0 to -22f, 14 to -30f, 28 to -22f)
    }

    val pull = clip("clip_pull", "Pull", ClipCategory.Action, 28, tags = listOf("effort")) {
        bone(B.CHEST, 0 to -8f, 14 to -16f, 28 to -8f)
        bone(B.ARM_L_UPPER, 0 to -95f, 14 to -60f, 28 to -95f)
        bone(B.ARM_R_UPPER, 0 to 95f, 14 to 60f, 28 to 95f)
        bone(B.LEG_L_UPPER, 0 to -18f, 14 to -26f, 28 to -18f)
    }

    val pickUp = clip("clip_pick_up", "Pick Up", ClipCategory.Action, 32, loopable = false, tags = listOf("object")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 12 to 60f, 24 to 0f, 32 to 0f)
        bone(B.LEG_L_UPPER, 0 to 0f, 12 to 72f, 24 to 0f)
        bone(B.LEG_R_UPPER, 0 to 0f, 12 to 72f, 24 to 0f)
        bone(B.LEG_L_LOWER, 0 to 0f, 12 to -80f, 24 to 0f)
        bone(B.LEG_R_LOWER, 0 to 0f, 12 to -80f, 24 to 0f)
        bone(B.ARM_L_UPPER, 0 to 0f, 12 to -55f, 24 to -20f, 32 to 0f)
        bone(B.ARM_R_UPPER, 0 to 0f, 12 to 55f, 24 to 20f, 32 to 0f)
        bone(B.CHEST, 0 to 0f, 12 to 30f, 24 to 0f)
    }

    val throwClip = clip("clip_throw", "Throw", ClipCategory.Action, 24, loopable = false, tags = listOf("object")) {
        bone(B.ARM_R_UPPER, 0 to 60f, 8 to 165f, 14 to -70f, 24 to 0f)
        bone(B.ARM_R_LOWER, 0 to 20f, 8 to 80f, 14 to -20f, 24 to 0f)
        bone(B.CHEST, 0 to -10f, 8 to -18f, 14 to 18f, 24 to 0f)
        bone(B.LEG_L_UPPER, 0 to 10f, 14 to -24f, 24 to 0f)
    }

    val dance = clip("clip_dance", "Dance", ClipCategory.Dance, 32, tags = listOf("party", "music")) {
        root(PropertyPath.POSITION_Y, 0 to 0f, 8 to -14f, 16 to 0f, 24 to -14f, 32 to 0f)
        root(PropertyPath.ROTATION, 0 to -4f, 16 to 4f, 32 to -4f)
        bone(B.ARM_L_UPPER, 0 to -140f, 8 to -60f, 16 to -140f, 24 to -60f, 32 to -140f)
        bone(B.ARM_R_UPPER, 0 to 60f, 8 to 140f, 16 to 60f, 24 to 140f, 32 to 60f)
        bone(B.LEG_L_UPPER, 0 to 18f, 16 to -18f, 32 to 18f)
        bone(B.LEG_R_UPPER, 0 to -18f, 16 to 18f, 32 to -18f)
        bone(B.HEAD, 0 to 6f, 16 to -6f, 32 to 6f)
    }

    val attack = clip("clip_attack", "Attack", ClipCategory.Combat, 20, loopable = false, tags = listOf("fight")) {
        root(PropertyPath.POSITION_X, 0 to 0f, 8 to 30f, 20 to 0f)
        bone(B.ARM_R_UPPER, 0 to 40f, 6 to 150f, 10 to -80f, 20 to 0f)
        bone(B.ARM_R_LOWER, 0 to 0f, 6 to 70f, 10 to -10f, 20 to 0f)
        bone(B.CHEST, 0 to -12f, 10 to 20f, 20 to 0f)
    }

    val defend = clip("clip_defend", "Defend", ClipCategory.Combat, 18, tags = listOf("block", "fight")) {
        bone(B.ARM_L_UPPER, 0 to 0f, 6 to -110f, 18 to -110f)
        bone(B.ARM_R_UPPER, 0 to 0f, 6 to 110f, 18 to 110f)
        bone(B.ARM_L_LOWER, 0 to 0f, 6 to -70f, 18 to -70f)
        bone(B.ARM_R_LOWER, 0 to 0f, 6 to 70f, 18 to 70f)
        bone(B.CHEST, 0 to 0f, 6 to 10f, 18 to 10f)
    }

    /** The complete built-in motion set. */
    val clips: List<AnimationClip> = listOf(
        idle, walk, run, jump, fall, land, crouch, sit, stand, turn,
        wave, point, talk, laugh, cry, angry, surprised, scared, celebrate, hurt,
        push, pull, pickUp, throwClip, dance, attack, defend,
    )

    val byId: Map<String, AnimationClip> = clips.associateBy { it.id }

    fun resolve(clipId: String): AnimationClip? = byId[clipId]

    fun inCategory(category: ClipCategory): List<AnimationClip> = clips.filter { it.category == category }

    fun search(query: String): List<AnimationClip> =
        if (query.isBlank()) clips else clips.filter { it.matches(query) }
}
