package com.cartoonstudio.domain.rigging

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.animation.PropertyTracks
import kotlinx.serialization.Serializable

/**
 * A single rig bone in its rest (bind) pose.
 *
 * Bones are defined in parent-local space; the rig solver resolves world
 * transforms each frame.
 */
@Serializable
data class Bone(
    val id: String,
    val name: String,
    val parentId: String? = null,
    /** Rest position relative to the parent bone's tip. */
    val restPosition: Vec2 = Vec2.ZERO,
    val restRotationDegrees: Float = 0f,
    val length: Float = 40f,
    /** Rotation limits in degrees; null means unconstrained. */
    val minRotationDegrees: Float? = null,
    val maxRotationDegrees: Float? = null,
    /** Artwork layers bound to this bone. */
    val boundLayerIds: List<String> = emptyList(),
    val colorArgb: Int = 0xFF4DD0E1.toInt(),
)

/** Extra rig behaviours evaluated after the bone hierarchy is resolved. */
@Serializable
sealed interface Constraint {
    val id: String
    val enabled: Boolean

    /** Two-bone inverse kinematics; the classic arm/leg solver. */
    @Serializable
    data class TwoBoneIk(
        override val id: String,
        val rootBoneId: String,
        val midBoneId: String,
        val tipBoneId: String,
        val targetPosition: Vec2,
        /** Positive bends one way, negative the other. */
        val poleSign: Float = 1f,
        val weight: Float = 1f,
        override val enabled: Boolean = true,
    ) : Constraint

    /** Copies another bone's rotation, optionally scaled. */
    @Serializable
    data class CopyRotation(
        override val id: String,
        val sourceBoneId: String,
        val targetBoneId: String,
        val factor: Float = 1f,
        override val enabled: Boolean = true,
    ) : Constraint

    /** Points a bone at a target, e.g. eyes following the camera. */
    @Serializable
    data class LookAt(
        override val id: String,
        val boneId: String,
        val target: Vec2,
        val offsetDegrees: Float = 0f,
        override val enabled: Boolean = true,
    ) : Constraint
}

/**
 * A character rig: bones, constraints and the rig profile that determines
 * which reusable animation clips can drive it.
 */
@Serializable
data class Skeleton(
    val id: String,
    val name: String,
    val bones: List<Bone> = emptyList(),
    val constraints: List<Constraint> = emptyList(),
    /**
     * Named rig layout (e.g. "biped.v1"). Clips authored for the same profile
     * retarget onto any character using it.
     */
    val profile: String = BIPED_PROFILE,
) {
    fun bone(id: String): Bone? = bones.firstOrNull { it.id == id }

    fun childrenOf(boneId: String?): List<Bone> = bones.filter { it.parentId == boneId }

    val roots: List<Bone> get() = bones.filter { it.parentId == null }

    fun withBone(bone: Bone): Skeleton {
        val index = bones.indexOfFirst { it.id == bone.id }
        return if (index < 0) copy(bones = bones + bone)
        else copy(bones = bones.toMutableList().also { it[index] = bone })
    }

    fun withoutBone(boneId: String): Skeleton {
        val removed = descendantsOf(boneId) + boneId
        return copy(
            bones = bones.filterNot { it.id in removed },
            constraints = constraints.filterNot { constraintTouches(it, removed) },
        )
    }

    private fun constraintTouches(constraint: Constraint, boneIds: Set<String>): Boolean =
        when (constraint) {
            is Constraint.TwoBoneIk ->
                constraint.rootBoneId in boneIds || constraint.midBoneId in boneIds ||
                    constraint.tipBoneId in boneIds
            is Constraint.CopyRotation ->
                constraint.sourceBoneId in boneIds || constraint.targetBoneId in boneIds
            is Constraint.LookAt -> constraint.boneId in boneIds
        }

    fun descendantsOf(boneId: String): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque(childrenOf(boneId).map { it.id })
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (result.add(next)) queue.addAll(childrenOf(next).map { it.id })
        }
        return result
    }

    companion object {
        const val BIPED_PROFILE = "biped.v1"
        const val QUADRUPED_PROFILE = "quadruped.v1"
        const val PROP_PROFILE = "prop.v1"
    }
}

/**
 * A saved pose: per-bone channel values at a single instant.
 *
 * Poses power the pose library, expression sheets and the "key from pose"
 * workflow in the timeline.
 */
@Serializable
data class Pose(
    val id: String,
    val name: String,
    /** bone id -> channel -> value. */
    val boneValues: Map<String, Map<String, Float>> = emptyMap(),
    val tags: List<String> = emptyList(),
    val builtIn: Boolean = true,
    val thumbnailRef: String? = null,
)

/** A facial expression, stored as a pose plus optional artwork swaps. */
@Serializable
data class Expression(
    val id: String,
    val name: String,
    val pose: Pose,
    /** layer id -> cel/variant name, for swap-based mouth and eye shapes. */
    val artworkSwaps: Map<String, String> = emptyMap(),
    val visemes: List<String> = emptyList(),
)

/** A rig plus its reusable motion, as shipped inside a character package. */
@Serializable
data class RigAnimationSet(
    val skeletonId: String,
    val poses: List<Pose> = emptyList(),
    val expressions: List<Expression> = emptyList(),
    val boneTracks: Map<String, PropertyTracks> = emptyMap(),
)
