package com.cartoonstudio.engine.rigging

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.math.toDegrees
import com.cartoonstudio.core.math.toRadians
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.rigging.Bone
import com.cartoonstudio.domain.rigging.Constraint
import com.cartoonstudio.domain.rigging.Pose
import com.cartoonstudio.domain.rigging.Skeleton
import kotlin.math.acos
import kotlin.math.atan2

/** Resolved world-space state of one bone at a single frame. */
data class SolvedBone(
    val id: String,
    val name: String,
    val worldMatrix: Matrix3,
    val headPosition: Vec2,
    val tailPosition: Vec2,
    val worldRotationDegrees: Float,
)

/** The whole rig resolved for one frame. */
data class SolvedSkeleton(
    val bones: Map<String, SolvedBone>,
) {
    fun bone(id: String): SolvedBone? = bones[id]

    /** Matrix that should be applied to artwork bound to [boneId]. */
    fun bindMatrixFor(boneId: String): Matrix3 = bones[boneId]?.worldMatrix ?: Matrix3.IDENTITY

    companion object {
        val Empty = SolvedSkeleton(emptyMap())
    }
}

/**
 * Forward kinematics with constraint post-processing.
 *
 * The solver walks the bone hierarchy once (parents before children), applies
 * the animated channel values, then runs constraints — two-bone IK, copy
 * rotation and look-at — which is enough for expressive cartoon rigs while
 * staying cheap enough to run every frame during playback.
 */
object RigSolver {

    fun solve(
        skeleton: Skeleton,
        boneValues: Map<String, Map<String, Float>> = emptyMap(),
        rootMatrix: Matrix3 = Matrix3.IDENTITY,
    ): SolvedSkeleton {
        if (skeleton.bones.isEmpty()) return SolvedSkeleton.Empty

        val ordered = topologicalOrder(skeleton)
        val local = HashMap<String, Float>()
        val solved = LinkedHashMap<String, SolvedBone>(ordered.size)

        for (bone in ordered) {
            val values = boneValues[bone.id].orEmpty()
            val rotation = bone.restRotationDegrees +
                (values[PropertyPath.ROTATION] ?: 0f)
            val clampedRotation = clampRotation(bone, rotation)
            local[bone.id] = clampedRotation

            val offset = Vec2(
                bone.restPosition.x + (values[PropertyPath.POSITION_X] ?: 0f),
                bone.restPosition.y + (values[PropertyPath.POSITION_Y] ?: 0f),
            )
            val scale = Vec2(
                values[PropertyPath.SCALE_X] ?: 1f,
                values[PropertyPath.SCALE_Y] ?: 1f,
            )

            val parentMatrix = bone.parentId?.let { solved[it]?.let { p -> tailMatrix(p) } } ?: rootMatrix
            val localMatrix = Matrix3.translation(offset) *
                Matrix3.rotation(clampedRotation.toRadians()) *
                Matrix3.scale(scale.x, scale.y)
            val world = parentMatrix * localMatrix

            val head = world.transform(Vec2.ZERO)
            val tail = world.transform(Vec2(bone.length, 0f))
            solved[bone.id] = SolvedBone(
                id = bone.id,
                name = bone.name,
                worldMatrix = world,
                headPosition = head,
                tailPosition = tail,
                worldRotationDegrees = atan2(tail.y - head.y, tail.x - head.x).toDegrees(),
            )
        }

        var result = SolvedSkeleton(solved)
        for (constraint in skeleton.constraints.filter { it.enabled }) {
            result = applyConstraint(skeleton, result, constraint, rootMatrix, boneValues)
        }
        return result
    }

    /** Applies a saved [pose] on top of the rig's rest state. */
    fun solvePose(skeleton: Skeleton, pose: Pose, rootMatrix: Matrix3 = Matrix3.IDENTITY): SolvedSkeleton =
        solve(skeleton, pose.boneValues, rootMatrix)

    private fun tailMatrix(parent: SolvedBone): Matrix3 {
        // Children attach at the parent's tip, which is the standard skeletal
        // convention and keeps limb chains intuitive to pose.
        return parent.worldMatrix * Matrix3.translation(
            Vec2(parent.headPosition.distanceTo(parent.tailPosition), 0f),
        )
    }

    private fun clampRotation(bone: Bone, rotation: Float): Float {
        var value = rotation
        bone.minRotationDegrees?.let { value = maxOf(value, it) }
        bone.maxRotationDegrees?.let { value = minOf(value, it) }
        return value
    }

    private fun topologicalOrder(skeleton: Skeleton): List<Bone> {
        val byId = skeleton.bones.associateBy { it.id }
        val visited = LinkedHashSet<String>()
        val ordered = ArrayList<Bone>(skeleton.bones.size)

        fun visit(bone: Bone, depth: Int) {
            if (depth > MAX_DEPTH || !visited.add(bone.id)) return
            bone.parentId?.let { parentId ->
                byId[parentId]?.let { if (it.id !in visited) visit(it, depth + 1) }
            }
            ordered += bone
        }

        skeleton.bones.forEach { visit(it, 0) }
        return ordered
    }

    private fun applyConstraint(
        skeleton: Skeleton,
        current: SolvedSkeleton,
        constraint: Constraint,
        rootMatrix: Matrix3,
        boneValues: Map<String, Map<String, Float>>,
    ): SolvedSkeleton = when (constraint) {
        is Constraint.TwoBoneIk -> solveTwoBoneIk(skeleton, current, constraint, rootMatrix, boneValues)
        is Constraint.CopyRotation -> {
            val source = current.bone(constraint.sourceBoneId)
            val target = current.bone(constraint.targetBoneId)
            if (source == null || target == null) current
            else {
                val delta = source.worldRotationDegrees * constraint.factor - target.worldRotationDegrees
                rotateBone(skeleton, current, target.id, delta, rootMatrix, boneValues)
            }
        }
        is Constraint.LookAt -> {
            val bone = current.bone(constraint.boneId)
            if (bone == null) current
            else {
                val desired = atan2(
                    constraint.target.y - bone.headPosition.y,
                    constraint.target.x - bone.headPosition.x,
                ).toDegrees() + constraint.offsetDegrees
                rotateBone(
                    skeleton, current, bone.id,
                    desired - bone.worldRotationDegrees, rootMatrix, boneValues,
                )
            }
        }
    }

    /**
     * Analytic two-bone IK.
     *
     * Solves the triangle formed by the upper bone, lower bone and the
     * root-to-target vector using the law of cosines; this is exact and has no
     * iteration cost, which matters during scrubbing.
     */
    private fun solveTwoBoneIk(
        skeleton: Skeleton,
        current: SolvedSkeleton,
        ik: Constraint.TwoBoneIk,
        rootMatrix: Matrix3,
        boneValues: Map<String, Map<String, Float>>,
    ): SolvedSkeleton {
        val rootBone = skeleton.bone(ik.rootBoneId) ?: return current
        val midBone = skeleton.bone(ik.midBoneId) ?: return current
        val solvedRoot = current.bone(ik.rootBoneId) ?: return current

        val upperLength = rootBone.length
        val lowerLength = midBone.length
        val toTarget = ik.targetPosition - solvedRoot.headPosition
        val distance = toTarget.length.coerceIn(
            kotlin.math.abs(upperLength - lowerLength) + 0.001f,
            upperLength + lowerLength - 0.001f,
        )

        val targetAngle = atan2(toTarget.y, toTarget.x)
        val cosRoot = ((upperLength * upperLength + distance * distance - lowerLength * lowerLength) /
            (2f * upperLength * distance)).coerceIn(-1f, 1f)
        val cosMid = ((upperLength * upperLength + lowerLength * lowerLength - distance * distance) /
            (2f * upperLength * lowerLength)).coerceIn(-1f, 1f)

        val rootAngle = targetAngle - acos(cosRoot) * (if (ik.poleSign >= 0f) 1f else -1f)
        val midAngle = (Math.PI.toFloat() - acos(cosMid)) * (if (ik.poleSign >= 0f) 1f else -1f)

        val overrides = HashMap<String, Map<String, Float>>(boneValues)
        overrides[ik.rootBoneId] = mergeChannel(
            boneValues[ik.rootBoneId], PropertyPath.ROTATION,
            rootAngle.toDegrees() - rootBone.restRotationDegrees, ik.weight,
        )
        overrides[ik.midBoneId] = mergeChannel(
            boneValues[ik.midBoneId], PropertyPath.ROTATION,
            midAngle.toDegrees() - midBone.restRotationDegrees, ik.weight,
        )
        return solve(skeleton.copy(constraints = emptyList()), overrides, rootMatrix)
    }

    private fun rotateBone(
        skeleton: Skeleton,
        current: SolvedSkeleton,
        boneId: String,
        deltaDegrees: Float,
        rootMatrix: Matrix3,
        boneValues: Map<String, Map<String, Float>>,
    ): SolvedSkeleton {
        if (kotlin.math.abs(deltaDegrees) < 0.01f) return current
        val overrides = HashMap<String, Map<String, Float>>(boneValues)
        val existing = boneValues[boneId]?.get(PropertyPath.ROTATION) ?: 0f
        overrides[boneId] = mergeChannel(boneValues[boneId], PropertyPath.ROTATION, existing + deltaDegrees, 1f)
        return solve(skeleton.copy(constraints = emptyList()), overrides, rootMatrix)
    }

    private fun mergeChannel(
        existing: Map<String, Float>?,
        channel: String,
        value: Float,
        weight: Float,
    ): Map<String, Float> {
        val base = existing?.toMutableMap() ?: HashMap()
        val previous = base[channel] ?: 0f
        base[channel] = previous + (value - previous) * weight.coerceIn(0f, 1f)
        return base
    }

    private const val MAX_DEPTH = 64
}
