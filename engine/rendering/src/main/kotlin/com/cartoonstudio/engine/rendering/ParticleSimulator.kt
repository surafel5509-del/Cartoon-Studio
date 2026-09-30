package com.cartoonstudio.engine.rendering

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.math.toRadians
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.model.ParticleEmitter
import com.cartoonstudio.domain.model.ParticlePreset
import kotlin.math.cos
import kotlin.math.sin

/** A single particle resolved at one frame. */
data class SimulatedParticle(
    val position: Vec2,
    val size: Float,
    val argb: Int,
    val rotationDegrees: Float,
)

/**
 * Deterministic, stateless particle simulation.
 *
 * Every particle's full trajectory is a closed-form function of its spawn
 * index and the emitter seed, so scrubbing backwards, exporting out of order
 * and re-rendering all give identical results — no simulation history to keep
 * in sync.
 */
object ParticleSimulator {

    fun simulate(emitter: ParticleEmitter, frame: Frame): List<SimulatedParticle> {
        val lifetime = emitter.lifetimeFrames.coerceAtLeast(1)
        val perFrame = (emitter.rate / 24f).coerceIn(0.05f, 20f)
        val result = ArrayList<SimulatedParticle>(64)

        val firstSpawnFrame = (frame.index - lifetime).coerceAtLeast(-lifetime)
        var spawnFrame = firstSpawnFrame
        var index = 0
        while (spawnFrame <= frame.index && index < MAX_PARTICLES) {
            val count = countForFrame(perFrame, spawnFrame, emitter.seed)
            for (n in 0 until count) {
                val age = frame.index - spawnFrame
                if (age < 0 || age > lifetime) continue
                val particle = particleAt(emitter, spawnFrame, n, age, lifetime)
                result += particle
                index++
                if (index >= MAX_PARTICLES) break
            }
            spawnFrame++
        }
        return result
    }

    private fun countForFrame(perFrame: Float, frameIndex: Int, seed: Int): Int {
        val whole = perFrame.toInt()
        val fraction = perFrame - whole
        val jitter = hash(frameIndex * 31 + seed)
        return whole + if (jitter < fraction) 1 else 0
    }

    private fun particleAt(
        emitter: ParticleEmitter,
        spawnFrame: Int,
        spawnIndex: Int,
        age: Int,
        lifetime: Int,
    ): SimulatedParticle {
        val key = spawnFrame * 7919 + spawnIndex * 104_729 + emitter.seed
        val r1 = hash(key)
        val r2 = hash(key + 1)
        val r3 = hash(key + 2)

        val spread = emitter.spreadDegrees
        val angle = (emitter.directionDegrees + (r1 - 0.5f) * spread * 2f).toRadians()
        val speed = emitter.speed * (0.6f + r2 * 0.8f)
        val t = age.toFloat() / lifetime
        val seconds = age / 24f

        val gravityOffset = 0.5f * emitter.gravity * seconds * seconds
        val position = Vec2(
            cos(angle) * speed * seconds + (r3 - 0.5f) * 12f,
            sin(angle) * speed * seconds + gravityOffset,
        )

        val size = emitter.startSize + (emitter.endSize - emitter.startSize) * t
        val color = emitter.startColor.lerp(emitter.endColor, t)
        return SimulatedParticle(
            position = applyPresetMotion(emitter.preset, position, t, r1),
            size = size.coerceAtLeast(0.5f),
            argb = color.argb,
            rotationDegrees = r2 * 360f + t * 180f,
        )
    }

    /** Per-preset character: drifting smoke, fluttering leaves, and so on. */
    private fun applyPresetMotion(preset: ParticlePreset, base: Vec2, t: Float, seed: Float): Vec2 =
        when (preset) {
            ParticlePreset.Smoke -> base + Vec2(sin(t * 6f + seed * 6f) * 18f * t, -t * 30f)
            ParticlePreset.Fire -> base + Vec2(sin(t * 12f + seed * 6f) * 8f, -t * 40f)
            ParticlePreset.Leaves -> base + Vec2(sin(t * 5f + seed * 8f) * 40f, 0f)
            ParticlePreset.Snow -> base + Vec2(sin(t * 3f + seed * 10f) * 26f, 0f)
            ParticlePreset.Bubbles -> base + Vec2(sin(t * 8f + seed * 4f) * 14f, -t * 22f)
            ParticlePreset.Confetti -> base + Vec2(sin(t * 9f + seed * 5f) * 30f, 0f)
            ParticlePreset.Magic -> base + Vec2(
                cos(t * 10f + seed * 7f) * 22f * (1f - t),
                sin(t * 10f + seed * 7f) * 22f * (1f - t),
            )
            else -> base
        }

    /** Deterministic hash in 0..1; the same input always yields the same value. */
    private fun hash(value: Int): Float {
        var x = value
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        return ((x and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat())
    }

    /** Default emitter tuning per preset, used when a VFX layer is created. */
    fun presetEmitter(id: String, preset: ParticlePreset): ParticleEmitter = when (preset) {
        ParticlePreset.Sparkle -> ParticleEmitter(id, preset, rate = 30f, lifetimeFrames = 24, speed = 70f, gravity = -20f, startSize = 10f, endSize = 0f, startColor = Rgba.of(255, 248, 190), endColor = Rgba.of(255, 200, 60, 0))
        ParticlePreset.Smoke -> ParticleEmitter(id, preset, rate = 16f, lifetimeFrames = 48, speed = 40f, gravity = -30f, startSize = 26f, endSize = 70f, startColor = Rgba.of(180, 180, 180, 160), endColor = Rgba.of(220, 220, 220, 0))
        ParticlePreset.Fire -> ParticleEmitter(id, preset, rate = 40f, lifetimeFrames = 18, speed = 90f, gravity = -120f, startSize = 22f, endSize = 4f, startColor = Rgba.of(255, 214, 80), endColor = Rgba.of(230, 70, 30, 0))
        ParticlePreset.Rain -> ParticleEmitter(id, preset, rate = 60f, lifetimeFrames = 30, speed = 40f, directionDegrees = 95f, gravity = 700f, startSize = 4f, endSize = 4f, startColor = Rgba.of(150, 200, 255, 200), endColor = Rgba.of(150, 200, 255, 60))
        ParticlePreset.Snow -> ParticleEmitter(id, preset, rate = 30f, lifetimeFrames = 90, speed = 20f, directionDegrees = 90f, gravity = 25f, startSize = 8f, endSize = 6f, startColor = Rgba.White, endColor = Rgba.of(255, 255, 255, 80))
        ParticlePreset.Explosion -> ParticleEmitter(id, preset, rate = 120f, lifetimeFrames = 20, speed = 320f, spreadDegrees = 180f, gravity = 200f, startSize = 18f, endSize = 2f, startColor = Rgba.of(255, 230, 140), endColor = Rgba.of(200, 60, 20, 0))
        else -> ParticleEmitter(id, preset)
    }

    private const val MAX_PARTICLES = 600
}
