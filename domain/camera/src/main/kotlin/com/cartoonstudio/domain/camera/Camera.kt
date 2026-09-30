package com.cartoonstudio.domain.camera

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.math.toRadians
import com.cartoonstudio.domain.animation.PropertyTracks
import kotlinx.serialization.Serializable

/**
 * A 2D production camera.
 *
 * The camera defines what the audience sees. Preview and export share the same
 * camera evaluation so a framing that looks right in the editor is exactly
 * what gets rendered.
 */
@Serializable
data class Camera(
    val id: String,
    val name: String = "Main Camera",
    val position: Vec2 = Vec2.ZERO,
    val zoom: Float = 1f,
    val rotationDegrees: Float = 0f,
    /** Depth-of-field style focus, reserved for the compositing stage. */
    val focus: Float = 0f,
    val tracks: PropertyTracks = PropertyTracks.Empty,
) {
    /**
     * World -> view matrix for a viewport of [viewportWidth] x [viewportHeight].
     *
     * The camera position marks the centre of frame.
     */
    fun viewMatrix(viewportWidth: Float, viewportHeight: Float): Matrix3 {
        val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return Matrix3.translation(Vec2(viewportWidth / 2f, viewportHeight / 2f)) *
            Matrix3.rotation((-rotationDegrees).toRadians()) *
            Matrix3.scale(safeZoom) *
            Matrix3.translation(-position)
    }

    /** The world-space rectangle currently framed by the camera. */
    fun frustum(viewportWidth: Float, viewportHeight: Float): Rect2 {
        val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val halfWidth = viewportWidth / (2f * safeZoom)
        val halfHeight = viewportHeight / (2f * safeZoom)
        return Rect2.fromCenter(position, halfWidth * 2f, halfHeight * 2f)
    }

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 32f
    }
}

/** Reusable camera behaviour that can be dropped onto any shot. */
@Serializable
data class CameraPreset(
    val id: String,
    val name: String,
    val description: String,
    val zoom: Float,
    val offset: Vec2 = Vec2.ZERO,
    val rotationDegrees: Float = 0f,
    val moveFrames: Int = 24,
) {
    companion object {
        val WideShot = CameraPreset("cam_wide", "Wide Shot", "Establishes the whole scene", 0.7f)
        val MediumShot = CameraPreset("cam_medium", "Medium Shot", "Waist up framing", 1.2f)
        val CloseUp = CameraPreset("cam_closeup", "Close Up", "Faces and reactions", 2.2f)
        val ExtremeCloseUp = CameraPreset("cam_ecu", "Extreme Close Up", "Eyes and detail", 3.6f)
        val DutchAngle = CameraPreset("cam_dutch", "Dutch Angle", "Tilted, uneasy framing", 1.4f, rotationDegrees = 12f)
        val PushIn = CameraPreset("cam_push_in", "Push In", "Slow move toward the subject", 1.6f, moveFrames = 48)
        val PullOut = CameraPreset("cam_pull_out", "Pull Out", "Reveal by moving away", 0.8f, moveFrames = 48)

        val builtIn = listOf(WideShot, MediumShot, CloseUp, ExtremeCloseUp, DutchAngle, PushIn, PullOut)
    }
}

/**
 * Multi-plane depth assignment.
 *
 * Layers with a parallax factor below 1 move slower than the camera (they read
 * as distant); factors above 1 move faster (foreground).
 */
@Serializable
data class ParallaxSettings(
    val enabled: Boolean = false,
    val factor: Float = 1f,
) {
    companion object {
        val Background = ParallaxSettings(true, 0.25f)
        val Midground = ParallaxSettings(true, 0.6f)
        val Subject = ParallaxSettings(false, 1f)
        val Foreground = ParallaxSettings(true, 1.6f)
        val Disabled = ParallaxSettings()
    }
}
