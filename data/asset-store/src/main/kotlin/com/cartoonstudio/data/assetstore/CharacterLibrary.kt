package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.model.AssetCategory
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetOrigin
import com.cartoonstudio.domain.model.CharacterPackage
import com.cartoonstudio.domain.model.CharacterVariant
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.Transform2D
import com.cartoonstudio.domain.rigging.Bone
import com.cartoonstudio.domain.rigging.Constraint
import com.cartoonstudio.domain.rigging.Expression
import com.cartoonstudio.domain.rigging.Pose
import com.cartoonstudio.domain.rigging.Skeleton

/** Stable bone ids shared by every biped character, so clips retarget freely. */
object BipedBones {
    const val HIPS = "hips"
    const val SPINE = "spine"
    const val CHEST = "chest"
    const val NECK = "neck"
    const val HEAD = "head"
    const val ARM_L_UPPER = "arm_l_upper"
    const val ARM_L_LOWER = "arm_l_lower"
    const val HAND_L = "hand_l"
    const val ARM_R_UPPER = "arm_r_upper"
    const val ARM_R_LOWER = "arm_r_lower"
    const val HAND_R = "hand_r"
    const val LEG_L_UPPER = "leg_l_upper"
    const val LEG_L_LOWER = "leg_l_lower"
    const val FOOT_L = "foot_l"
    const val LEG_R_UPPER = "leg_r_upper"
    const val LEG_R_LOWER = "leg_r_lower"
    const val FOOT_R = "foot_r"

    val all = listOf(
        HIPS, SPINE, CHEST, NECK, HEAD,
        ARM_L_UPPER, ARM_L_LOWER, HAND_L,
        ARM_R_UPPER, ARM_R_LOWER, HAND_R,
        LEG_L_UPPER, LEG_L_LOWER, FOOT_L,
        LEG_R_UPPER, LEG_R_LOWER, FOOT_R,
    )

    val upperBody = listOf(SPINE, CHEST, NECK, HEAD, ARM_L_UPPER, ARM_L_LOWER, HAND_L, ARM_R_UPPER, ARM_R_LOWER, HAND_R)
    val lowerBody = listOf(HIPS, LEG_L_UPPER, LEG_L_LOWER, FOOT_L, LEG_R_UPPER, LEG_R_LOWER, FOOT_R)

    fun displayName(boneId: String): String =
        boneId.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/** Body proportions; changing these alone produces very different casts. */
data class CharacterProportions(
    val height: Float = 320f,
    val headScale: Float = 1f,
    val bodyWidth: Float = 1f,
    val limbThickness: Float = 1f,
    val legLengthFactor: Float = 1f,
) {
    companion object {
        val Adult = CharacterProportions()
        val Child = CharacterProportions(height = 230f, headScale = 1.35f, legLengthFactor = 0.8f)
        val Chibi = CharacterProportions(height = 190f, headScale = 1.9f, bodyWidth = 1.2f, legLengthFactor = 0.55f)
        val Tall = CharacterProportions(height = 390f, headScale = 0.85f, limbThickness = 0.85f, legLengthFactor = 1.2f)
        val Stocky = CharacterProportions(height = 280f, headScale = 1.1f, bodyWidth = 1.45f, limbThickness = 1.35f)
    }
}

/** Colour scheme applied to a character's artwork. */
data class CharacterStyle(
    val skin: Rgba,
    val hair: Rgba,
    val top: Rgba,
    val bottom: Rgba,
    val shoes: Rgba,
    val accent: Rgba = top,
)

/**
 * Builds complete, animatable character packages.
 *
 * Each package contains a standard biped rig, layered vector artwork whose
 * layers are named after the bones that drive them, pose and expression
 * sheets, and access to the shared reusable motion library.
 */
object CharacterFactory {

    fun skeleton(id: String, proportions: CharacterProportions): Skeleton {
        val unit = proportions.height / 320f
        val legLength = 70f * unit * proportions.legLengthFactor
        val armLength = 58f * unit

        val bones = listOf(
            Bone(BipedBones.HIPS, "Hips", null, Vec2(0f, 0f), -90f, 42f * unit),
            Bone(BipedBones.SPINE, "Spine", BipedBones.HIPS, Vec2.ZERO, 0f, 40f * unit),
            Bone(BipedBones.CHEST, "Chest", BipedBones.SPINE, Vec2.ZERO, 0f, 34f * unit),
            Bone(BipedBones.NECK, "Neck", BipedBones.CHEST, Vec2.ZERO, 0f, 16f * unit),
            Bone(BipedBones.HEAD, "Head", BipedBones.NECK, Vec2.ZERO, 0f, 48f * unit * proportions.headScale),

            Bone(BipedBones.ARM_L_UPPER, "Arm L Upper", BipedBones.CHEST, Vec2(0f, -22f * unit), 150f, armLength),
            Bone(BipedBones.ARM_L_LOWER, "Arm L Lower", BipedBones.ARM_L_UPPER, Vec2.ZERO, 12f, armLength),
            Bone(BipedBones.HAND_L, "Hand L", BipedBones.ARM_L_LOWER, Vec2.ZERO, 0f, 16f * unit),

            Bone(BipedBones.ARM_R_UPPER, "Arm R Upper", BipedBones.CHEST, Vec2(0f, 22f * unit), 30f, armLength),
            Bone(BipedBones.ARM_R_LOWER, "Arm R Lower", BipedBones.ARM_R_UPPER, Vec2.ZERO, -12f, armLength),
            Bone(BipedBones.HAND_R, "Hand R", BipedBones.ARM_R_LOWER, Vec2.ZERO, 0f, 16f * unit),

            Bone(BipedBones.LEG_L_UPPER, "Leg L Upper", BipedBones.HIPS, Vec2(0f, -14f * unit), 175f, legLength),
            Bone(BipedBones.LEG_L_LOWER, "Leg L Lower", BipedBones.LEG_L_UPPER, Vec2.ZERO, 5f, legLength),
            Bone(BipedBones.FOOT_L, "Foot L", BipedBones.LEG_L_LOWER, Vec2.ZERO, -85f, 24f * unit),

            Bone(BipedBones.LEG_R_UPPER, "Leg R Upper", BipedBones.HIPS, Vec2(0f, 14f * unit), 185f, legLength),
            Bone(BipedBones.LEG_R_LOWER, "Leg R Lower", BipedBones.LEG_R_UPPER, Vec2.ZERO, -5f, legLength),
            Bone(BipedBones.FOOT_R, "Foot R", BipedBones.LEG_R_LOWER, Vec2.ZERO, -85f, 24f * unit),
        )

        return Skeleton(
            id = id,
            name = "Biped Rig",
            bones = bones,
            constraints = listOf(
                Constraint.TwoBoneIk(
                    id = "${id}_ik_leg_l",
                    rootBoneId = BipedBones.LEG_L_UPPER,
                    midBoneId = BipedBones.LEG_L_LOWER,
                    tipBoneId = BipedBones.FOOT_L,
                    targetPosition = Vec2(-18f, legLength * 2f),
                    enabled = false,
                ),
                Constraint.TwoBoneIk(
                    id = "${id}_ik_leg_r",
                    rootBoneId = BipedBones.LEG_R_UPPER,
                    midBoneId = BipedBones.LEG_R_LOWER,
                    tipBoneId = BipedBones.FOOT_R,
                    targetPosition = Vec2(18f, legLength * 2f),
                    enabled = false,
                ),
            ),
            profile = Skeleton.BIPED_PROFILE,
        )
    }

    /**
     * Layered artwork. Each layer is named after the bone that drives it, so
     * applying a clip to a character instance simply routes bone channels onto
     * the matching layer transform.
     */
    fun artwork(style: CharacterStyle, proportions: CharacterProportions): List<Layer> {
        val unit = proportions.height / 320f
        val limb = 18f * unit * proportions.limbThickness
        val torsoWidth = 76f * unit * proportions.bodyWidth
        val legLength = 70f * unit * proportions.legLengthFactor
        val armLength = 58f * unit
        val headRadius = 46f * unit * proportions.headScale

        fun part(boneId: String, name: String, strokes: List<Stroke>, offset: Vec2) = Layer(
            id = "art_$boneId",
            name = name,
            content = LayerContent.Drawing(cels = mapOf(0 to Cel(id = "cel_$boneId", strokes = strokes))),
            transform = Transform2D(position = offset),
        )

        // Drawn back-to-front: far limbs, body, near limbs, head.
        return listOf(
            part(
                BipedBones.HEAD, "Head",
                listOf(
                    ShapeFactory.circle(Vec2.ZERO, headRadius, style.skin),
                    ShapeFactory.ellipse(
                        Vec2(0f, -headRadius * 0.45f), headRadius * 1.02f, headRadius * 0.62f, style.hair,
                    ),
                    ShapeFactory.circle(Vec2(-headRadius * 0.34f, -headRadius * 0.05f), headRadius * 0.12f, Rgba.Ink),
                    ShapeFactory.circle(Vec2(headRadius * 0.34f, -headRadius * 0.05f), headRadius * 0.12f, Rgba.Ink),
                    ShapeFactory.arc(
                        Vec2(0f, headRadius * 0.18f), headRadius * 0.42f, 20f, 140f, Rgba.Ink, 4f * unit,
                    ),
                ),
                Vec2(0f, -(proportions.height * 0.78f)),
            ),
            part(
                BipedBones.HAND_L, "Hand L",
                listOf(ShapeFactory.circle(Vec2.ZERO, limb * 0.62f, style.skin)),
                Vec2(-torsoWidth * 0.62f, -proportions.height * 0.34f),
            ),
            part(
                BipedBones.ARM_L_LOWER, "Arm L Lower",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, armLength), limb * 0.82f, style.skin)),
                Vec2(-torsoWidth * 0.55f, -proportions.height * 0.48f),
            ),
            part(
                BipedBones.ARM_L_UPPER, "Arm L Upper",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, armLength), limb, style.top)),
                Vec2(-torsoWidth * 0.46f, -proportions.height * 0.62f),
            ),
            part(
                BipedBones.FOOT_L, "Foot L",
                listOf(ShapeFactory.roundedRect(Vec2(-4f * unit, 0f), 34f * unit, 16f * unit, 7f * unit, style.shoes)),
                Vec2(-torsoWidth * 0.26f, -proportions.height * 0.02f),
            ),
            part(
                BipedBones.LEG_L_LOWER, "Leg L Lower",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, legLength), limb * 0.92f, style.bottom)),
                Vec2(-torsoWidth * 0.26f, -proportions.height * 0.24f),
            ),
            part(
                BipedBones.LEG_L_UPPER, "Leg L Upper",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, legLength), limb * 1.05f, style.bottom)),
                Vec2(-torsoWidth * 0.26f, -proportions.height * 0.44f),
            ),
            part(
                BipedBones.CHEST, "Torso",
                listOf(
                    ShapeFactory.roundedRect(
                        Vec2.ZERO, torsoWidth, proportions.height * 0.34f, 22f * unit, style.top,
                    ),
                    ShapeFactory.roundedRect(
                        Vec2(0f, proportions.height * 0.17f), torsoWidth * 0.94f,
                        proportions.height * 0.1f, 12f * unit, style.bottom,
                    ),
                ),
                Vec2(0f, -proportions.height * 0.52f),
            ),
            part(
                BipedBones.FOOT_R, "Foot R",
                listOf(ShapeFactory.roundedRect(Vec2(-4f * unit, 0f), 34f * unit, 16f * unit, 7f * unit, style.shoes)),
                Vec2(torsoWidth * 0.26f, -proportions.height * 0.02f),
            ),
            part(
                BipedBones.LEG_R_LOWER, "Leg R Lower",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, legLength), limb * 0.92f, style.bottom)),
                Vec2(torsoWidth * 0.26f, -proportions.height * 0.24f),
            ),
            part(
                BipedBones.LEG_R_UPPER, "Leg R Upper",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, legLength), limb * 1.05f, style.bottom)),
                Vec2(torsoWidth * 0.26f, -proportions.height * 0.44f),
            ),
            part(
                BipedBones.ARM_R_UPPER, "Arm R Upper",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, armLength), limb, style.top)),
                Vec2(torsoWidth * 0.46f, -proportions.height * 0.62f),
            ),
            part(
                BipedBones.ARM_R_LOWER, "Arm R Lower",
                listOf(ShapeFactory.limb(Vec2.ZERO, Vec2(0f, armLength), limb * 0.82f, style.skin)),
                Vec2(torsoWidth * 0.55f, -proportions.height * 0.48f),
            ),
            part(
                BipedBones.HAND_R, "Hand R",
                listOf(ShapeFactory.circle(Vec2.ZERO, limb * 0.62f, style.skin)),
                Vec2(torsoWidth * 0.62f, -proportions.height * 0.34f),
            ),
        )
    }

    fun poses(packageId: String): List<Pose> = listOf(
        Pose(
            "${packageId}_pose_stand", "Stand",
            mapOf(
                BipedBones.ARM_L_UPPER to mapOf(ROTATION to -8f),
                BipedBones.ARM_R_UPPER to mapOf(ROTATION to 8f),
            ),
            tags = listOf("neutral", "idle"),
        ),
        Pose(
            "${packageId}_pose_wave", "Wave",
            mapOf(
                BipedBones.ARM_R_UPPER to mapOf(ROTATION to -120f),
                BipedBones.ARM_R_LOWER to mapOf(ROTATION to -35f),
            ),
            tags = listOf("greeting", "gesture"),
        ),
        Pose(
            "${packageId}_pose_point", "Point",
            mapOf(
                BipedBones.ARM_R_UPPER to mapOf(ROTATION to -75f),
                BipedBones.ARM_R_LOWER to mapOf(ROTATION to 10f),
            ),
            tags = listOf("gesture", "explain"),
        ),
        Pose(
            "${packageId}_pose_run", "Run Contact",
            mapOf(
                BipedBones.LEG_L_UPPER to mapOf(ROTATION to 38f),
                BipedBones.LEG_R_UPPER to mapOf(ROTATION to -38f),
                BipedBones.ARM_L_UPPER to mapOf(ROTATION to -42f),
                BipedBones.ARM_R_UPPER to mapOf(ROTATION to 42f),
            ),
            tags = listOf("action", "locomotion"),
        ),
        Pose(
            "${packageId}_pose_sit", "Sit",
            mapOf(
                BipedBones.LEG_L_UPPER to mapOf(ROTATION to 85f),
                BipedBones.LEG_R_UPPER to mapOf(ROTATION to 85f),
                BipedBones.LEG_L_LOWER to mapOf(ROTATION to -85f),
                BipedBones.LEG_R_LOWER to mapOf(ROTATION to -85f),
            ),
            tags = listOf("idle"),
        ),
        Pose(
            "${packageId}_pose_cheer", "Cheer",
            mapOf(
                BipedBones.ARM_L_UPPER to mapOf(ROTATION to -150f),
                BipedBones.ARM_R_UPPER to mapOf(ROTATION to 150f),
            ),
            tags = listOf("emotion", "celebrate"),
        ),
    )

    fun expressions(packageId: String): List<Expression> = listOf(
        "Neutral", "Happy", "Sad", "Angry", "Surprised", "Scared", "Laughing", "Thinking",
    ).mapIndexed { index, name ->
        Expression(
            id = "${packageId}_expr_${name.lowercase()}",
            name = name,
            pose = Pose("${packageId}_expr_pose_$index", name, emptyMap(), builtIn = true),
            artworkSwaps = mapOf("art_${BipedBones.HEAD}" to name.lowercase()),
        )
    }

    fun variants(packageId: String): List<CharacterVariant> = listOf(
        CharacterVariant("${packageId}_var_default", "Default"),
        CharacterVariant("${packageId}_var_alt", "Alternate Outfit"),
        CharacterVariant("${packageId}_var_night", "Night"),
    )

    const val ROTATION = "transform.rotation"
}

/**
 * The built-in cast.
 *
 * Thirty-two character packages across archetypes, each sharing the
 * `biped.v1` rig profile so the entire reusable motion library applies to
 * every one of them.
 */
object CharacterLibrary {

    private data class Blueprint(
        val name: String,
        val tags: List<String>,
        val proportions: CharacterProportions,
        val style: CharacterStyle,
    )

    private fun style(skin: Long, hair: Long, top: Long, bottom: Long, shoes: Long) = CharacterStyle(
        skin = Rgba(skin.toInt()), hair = Rgba(hair.toInt()), top = Rgba(top.toInt()),
        bottom = Rgba(bottom.toInt()), shoes = Rgba(shoes.toInt()),
    )

    private val blueprints: List<Blueprint> = listOf(
        Blueprint("Mia", listOf("lead", "girl", "modern"), CharacterProportions.Adult, style(0xFFFFE0BD, 0xFF4E342E, 0xFFE91E63, 0xFF3F51B5, 0xFF212121)),
        Blueprint("Leo", listOf("lead", "boy", "modern"), CharacterProportions.Adult, style(0xFFF1C27D, 0xFF212121, 0xFF2196F3, 0xFF455A64, 0xFF795548)),
        Blueprint("Pip", listOf("child", "sidekick"), CharacterProportions.Child, style(0xFFFFE0BD, 0xFFFFC107, 0xFF4CAF50, 0xFF8D6E63, 0xFFF44336)),
        Blueprint("Nia", listOf("child", "school"), CharacterProportions.Child, style(0xFF8D5524, 0xFF1A1A1F, 0xFFFF9800, 0xFF3F51B5, 0xFFFFFFFF)),
        Blueprint("Bobo", listOf("chibi", "comedy"), CharacterProportions.Chibi, style(0xFFFFCC99, 0xFFFF5722, 0xFFFFEB3B, 0xFF009688, 0xFF3E2723)),
        Blueprint("Tilly", listOf("chibi", "fairy"), CharacterProportions.Chibi, style(0xFFFFE0BD, 0xFFBA68C8, 0xFFE1BEE7, 0xFF9C27B0, 0xFFFFC107)),
        Blueprint("Grim", listOf("villain", "tall"), CharacterProportions.Tall, style(0xFFCFD8DC, 0xFF263238, 0xFF37474F, 0xFF212121, 0xFF000000)),
        Blueprint("Vera", listOf("villain", "elegant"), CharacterProportions.Tall, style(0xFFEFD6C0, 0xFF6A1B9A, 0xFF4A148C, 0xFF1A1A1F, 0xFF880E4F)),
        Blueprint("Bruno", listOf("stocky", "strong"), CharacterProportions.Stocky, style(0xFFC68642, 0xFF3E2723, 0xFFD32F2F, 0xFF5D4037, 0xFF212121)),
        Blueprint("Hana", listOf("adult", "scientist"), CharacterProportions.Adult, style(0xFFFFE0BD, 0xFF212121, 0xFFFFFFFF, 0xFF607D8B, 0xFF9E9E9E)),
        Blueprint("Sam", listOf("adult", "explorer"), CharacterProportions.Adult, style(0xFFE0AC69, 0xFF795548, 0xFF8BC34A, 0xFF6D4C41, 0xFF4E342E)),
        Blueprint("Ivy", listOf("adult", "artist"), CharacterProportions.Adult, style(0xFFFFDBAC, 0xFF00BCD4, 0xFFFF7043, 0xFF37474F, 0xFFFFFFFF)),
        Blueprint("Rex", listOf("hero", "action"), CharacterProportions.Stocky, style(0xFFD2A679, 0xFF212121, 0xFF1565C0, 0xFF0D47A1, 0xFFB71C1C)),
        Blueprint("Zuri", listOf("hero", "action"), CharacterProportions.Adult, style(0xFF6D4C41, 0xFF1A1A1F, 0xFF00897B, 0xFF004D40, 0xFFFFD54F)),
        Blueprint("Momo", listOf("mascot", "cute"), CharacterProportions.Chibi, style(0xFFFFF3E0, 0xFFF48FB1, 0xFFF8BBD0, 0xFFF06292, 0xFFFFFFFF)),
        Blueprint("Ollie", listOf("mascot", "robot"), CharacterProportions.Chibi, style(0xFFB0BEC5, 0xFF546E7A, 0xFF90A4AE, 0xFF607D8B, 0xFF37474F)),
        Blueprint("Nora", listOf("elder", "kind"), CharacterProportions.Adult, style(0xFFF5DEB3, 0xFFE0E0E0, 0xFF9575CD, 0xFF5E35B1, 0xFF4E342E)),
        Blueprint("Walter", listOf("elder", "grumpy"), CharacterProportions.Stocky, style(0xFFE8C39E, 0xFFBDBDBD, 0xFF6D4C41, 0xFF4E342E, 0xFF3E2723)),
        Blueprint("Kai", listOf("teen", "skater"), CharacterProportions.Adult, style(0xFFD2A679, 0xFF00E5FF, 0xFF212121, 0xFF424242, 0xFFFFEB3B)),
        Blueprint("Suki", listOf("teen", "student"), CharacterProportions.Adult, style(0xFFFFE0BD, 0xFF4A148C, 0xFFFFFFFF, 0xFFC2185B, 0xFF212121)),
        Blueprint("Dot", listOf("child", "toddler"), CharacterProportions.Chibi, style(0xFFFFDBAC, 0xFFFFCA28, 0xFF4FC3F7, 0xFFFFFFFF, 0xFFEF5350)),
        Blueprint("Milo", listOf("child", "curious"), CharacterProportions.Child, style(0xFFF1C27D, 0xFF6D4C41, 0xFF7CB342, 0xFF33691E, 0xFF795548)),
        Blueprint("Astra", listOf("space", "hero"), CharacterProportions.Adult, style(0xFFFFE0BD, 0xFFE1F5FE, 0xFFECEFF1, 0xFF455A64, 0xFF0288D1)),
        Blueprint("Nox", listOf("space", "villain"), CharacterProportions.Tall, style(0xFF90A4AE, 0xFF1A1A1F, 0xFF212121, 0xFF311B92, 0xFF000000)),
        Blueprint("Fern", listOf("nature", "guide"), CharacterProportions.Adult, style(0xFFE0AC69, 0xFF2E7D32, 0xFF66BB6A, 0xFF4E342E, 0xFF3E2723)),
        Blueprint("Coral", listOf("nature", "ocean"), CharacterProportions.Adult, style(0xFFFFCC99, 0xFF26C6DA, 0xFF00ACC1, 0xFF006064, 0xFFFFF176)),
        Blueprint("Chef Remy", listOf("job", "chef"), CharacterProportions.Stocky, style(0xFFF1C27D, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF212121)),
        Blueprint("Doctor Ada", listOf("job", "doctor"), CharacterProportions.Adult, style(0xFF8D5524, 0xFF212121, 0xFFFFFFFF, 0xFF0277BD, 0xFFFFFFFF)),
        Blueprint("Officer Ben", listOf("job", "police"), CharacterProportions.Stocky, style(0xFFE8C39E, 0xFF3E2723, 0xFF1A237E, 0xFF283593, 0xFF212121)),
        Blueprint("Pilot Jun", listOf("job", "pilot"), CharacterProportions.Adult, style(0xFFFFE0BD, 0xFF212121, 0xFF37474F, 0xFF263238, 0xFF795548)),
        Blueprint("Gus", listOf("comedy", "sidekick"), CharacterProportions.Stocky, style(0xFFD7A86E, 0xFFFF7043, 0xFFFFCA28, 0xFF6D4C41, 0xFF4E342E)),
        Blueprint("Lumen", listOf("fantasy", "magic"), CharacterProportions.Tall, style(0xFFFFF8E1, 0xFF7E57C2, 0xFF5E35B1, 0xFF311B92, 0xFFFFD740)),
    )

    /** Every built-in character package, built lazily on first access. */
    val packages: List<CharacterPackage> by lazy {
        blueprints.mapIndexed { index, blueprint ->
            val packageId = "char_${blueprint.name.lowercase().replace(' ', '_')}"
            CharacterPackage(
                descriptor = AssetDescriptor(
                    id = packageId,
                    name = blueprint.name,
                    category = AssetCategory.Character,
                    origin = AssetOrigin.BuiltIn,
                    tags = blueprint.tags + listOf("character", "biped", "rigged"),
                    packId = BuiltInPacks.CAST,
                    rigProfile = Skeleton.BIPED_PROFILE,
                    description = "${blueprint.name} — rigged biped with ${AnimationLibrary.clips.size} reusable motions",
                    createdAtMillis = 1_700_000_000_000L + index,
                ),
                skeleton = CharacterFactory.skeleton("${packageId}_rig", blueprint.proportions),
                artworkLayers = CharacterFactory.artwork(blueprint.style, blueprint.proportions),
                poses = CharacterFactory.poses(packageId),
                expressions = CharacterFactory.expressions(packageId),
                clips = AnimationLibrary.clips,
                variants = CharacterFactory.variants(packageId),
                defaultPoseId = "${packageId}_pose_stand",
            )
        }
    }

    fun byId(id: String): CharacterPackage? = packages.firstOrNull { it.id == id }

    fun search(query: String): List<CharacterPackage> =
        if (query.isBlank()) packages else packages.filter { it.descriptor.matches(query) }
}

/** Ids of the content packs that ship with the app. */
object BuiltInPacks {
    const val CAST = "pack_core_cast"
    const val MOTION = "pack_core_motion"
    const val WORLD = "pack_core_world"
    const val EFFECTS = "pack_core_effects"
    const val AUDIO = "pack_core_audio"
    const val PRODUCTION = "pack_core_production"
}
