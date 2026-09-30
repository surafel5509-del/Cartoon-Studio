package com.cartoonstudio.domain.drawing

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Packed sRGB colour with alpha, stored as `0xAARRGGBB`.
 *
 * A packed int keeps documents compact and avoids float drift when a project
 * is saved and reloaded repeatedly.
 */
@Serializable
@JvmInline
value class Rgba(val argb: Int) {

    val alpha: Int get() = (argb ushr 24) and 0xFF
    val red: Int get() = (argb ushr 16) and 0xFF
    val green: Int get() = (argb ushr 8) and 0xFF
    val blue: Int get() = argb and 0xFF

    val alphaFraction: Float get() = alpha / 255f

    fun withAlpha(alpha: Int): Rgba = Rgba((argb and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24))

    fun withAlphaFraction(fraction: Float): Rgba = withAlpha((fraction.coerceIn(0f, 1f) * 255f).roundToInt())

    fun lerp(other: Rgba, t: Float): Rgba {
        val f = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * f).roundToInt().coerceIn(0, 255)
        return of(mix(red, other.red), mix(green, other.green), mix(blue, other.blue), mix(alpha, other.alpha))
    }

    /** Perceptual luminance, used to pick readable foreground colours. */
    val luminance: Float get() = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f

    fun toHex(): String = "#%08X".format(argb)

    fun toHsv(): FloatArray {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val cMax = max(r, max(g, b))
        val cMin = min(r, min(g, b))
        val delta = cMax - cMin
        val hue = when {
            delta < 1e-5f -> 0f
            cMax == r -> 60f * (((g - b) / delta) % 6f)
            cMax == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (cMax <= 0f) 0f else delta / cMax
        return floatArrayOf(if (hue < 0) hue + 360f else hue, saturation, cMax)
    }

    companion object {
        fun of(r: Int, g: Int, b: Int, a: Int = 255): Rgba = Rgba(
            ((a.coerceIn(0, 255)) shl 24) or
                ((r.coerceIn(0, 255)) shl 16) or
                ((g.coerceIn(0, 255)) shl 8) or
                b.coerceIn(0, 255)
        )

        fun fromHsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Rgba {
            val h = ((hue % 360f) + 360f) % 360f
            val s = saturation.coerceIn(0f, 1f)
            val v = value.coerceIn(0f, 1f)
            val c = v * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = v - c
            val (r, g, b) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return of(
                ((r + m) * 255f).roundToInt(),
                ((g + m) * 255f).roundToInt(),
                ((b + m) * 255f).roundToInt(),
                (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
            )
        }

        fun parse(hex: String): Rgba? {
            val cleaned = hex.removePrefix("#")
            val value = cleaned.toLongOrNull(16) ?: return null
            return when (cleaned.length) {
                6 -> Rgba((0xFF000000L or value).toInt())
                8 -> Rgba(value.toInt())
                else -> null
            }
        }

        val Transparent = Rgba(0x00000000)
        val Black = Rgba(0xFF000000.toInt())
        val White = Rgba(0xFFFFFFFF.toInt())
        val Ink = Rgba(0xFF1A1A1F.toInt())
        val Paper = Rgba(0xFFFDFBF7.toInt())
    }
}

/** Named, reusable colour palette shipped with the app or authored by a user. */
@Serializable
data class Palette(
    val id: String,
    val name: String,
    val colors: List<Rgba>,
    val builtIn: Boolean = true,
) {
    companion object {
        val CartoonBasics = Palette(
            id = "palette_cartoon_basics",
            name = "Cartoon Basics",
            colors = listOf(
                Rgba.of(26, 26, 31), Rgba.of(255, 255, 255), Rgba.of(244, 67, 54),
                Rgba.of(255, 152, 0), Rgba.of(255, 214, 0), Rgba.of(76, 175, 80),
                Rgba.of(0, 188, 212), Rgba.of(33, 150, 243), Rgba.of(103, 58, 183),
                Rgba.of(233, 30, 99), Rgba.of(121, 85, 72), Rgba.of(158, 158, 158),
            ),
        )

        val SkinTones = Palette(
            id = "palette_skin_tones",
            name = "Skin Tones",
            colors = listOf(
                Rgba.of(255, 224, 189), Rgba.of(241, 194, 125), Rgba.of(224, 172, 105),
                Rgba.of(198, 134, 66), Rgba.of(141, 85, 36), Rgba.of(94, 58, 28),
            ),
        )

        val Environment = Palette(
            id = "palette_environment",
            name = "Environment",
            colors = listOf(
                Rgba.of(141, 211, 255), Rgba.of(96, 173, 235), Rgba.of(120, 190, 100),
                Rgba.of(74, 148, 70), Rgba.of(46, 105, 60), Rgba.of(200, 178, 130),
                Rgba.of(150, 120, 85), Rgba.of(90, 70, 55),
            ),
        )

        val builtIn = listOf(CartoonBasics, SkinTones, Environment)
    }
}
