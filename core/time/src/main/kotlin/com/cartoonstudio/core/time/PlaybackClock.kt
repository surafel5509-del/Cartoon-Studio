package com.cartoonstudio.core.time

/**
 * Deterministic playback clock.
 *
 * The clock advances by wall-clock delta so playback stays real-time even when
 * the renderer drops frames; the editor separately reports the achieved frame
 * rate for the performance HUD.
 */
class PlaybackClock(
    private var rate: FrameRate = FrameRate.Default,
    private var range: FrameRange = FrameRange.ofLength(Frame.ZERO, 48),
) {

    var isPlaying: Boolean = false
        private set

    var loop: Boolean = true

    /** Playback speed multiplier (0.25x .. 4x). */
    var speed: Float = 1f
        set(value) {
            field = value.coerceIn(0.1f, 8f)
        }

    private var positionMillis: Double = 0.0
    private var accumulatedMillis: Double = 0.0
    private var measuredFps: Double = 0.0

    val currentFrame: Frame
        get() = range.clamp(Frame(range.start.index + (positionMillis / rate.frameDurationMillis).toInt()))

    val achievedFps: Double get() = measuredFps

    fun configure(rate: FrameRate, range: FrameRange) {
        this.rate = rate
        this.range = range
        seekTo(currentFrame)
    }

    fun play() {
        if (range.isEmpty) return
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
    }

    fun toggle() = if (isPlaying) pause() else play()

    fun stop() {
        isPlaying = false
        seekTo(range.start)
    }

    fun seekTo(frame: Frame) {
        val clamped = range.clamp(frame)
        positionMillis = (clamped.index - range.start.index) * rate.frameDurationMillis
    }

    fun step(frames: Int) {
        pause()
        seekTo(currentFrame + frames)
    }

    /**
     * Advances the clock. Returns true when the visible frame changed and the
     * renderer therefore needs to produce a new image.
     */
    fun advance(deltaMillis: Double): Boolean {
        if (deltaMillis > 0) {
            measuredFps = (1000.0 / deltaMillis).coerceAtMost(999.0)
        }
        if (!isPlaying) return false
        val before = currentFrame
        positionMillis += deltaMillis * speed
        accumulatedMillis += deltaMillis

        val totalMillis = range.lengthInFrames * rate.frameDurationMillis
        if (positionMillis >= totalMillis) {
            if (loop && totalMillis > 0) {
                positionMillis %= totalMillis
            } else {
                positionMillis = (totalMillis - rate.frameDurationMillis).coerceAtLeast(0.0)
                isPlaying = false
            }
        }
        return currentFrame != before
    }
}
