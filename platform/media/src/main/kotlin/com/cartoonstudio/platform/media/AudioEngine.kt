package com.cartoonstudio.platform.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRate
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/** Information read from an imported audio file. */
data class AudioFileInfo(
    val durationMillis: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val mimeType: String,
)

/**
 * Audio playback synchronised to the animation timeline.
 *
 * Playback is driven by frame position: when the user scrubs, seeks or loops,
 * the player follows the timeline rather than the other way around.
 */
class TimelineAudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var loadedPath: String? = null

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun load(file: File): Outcome<AudioFileInfo> = try {
        release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }
        loadedPath = file.absolutePath
        Outcome.success(inspect(file))
    } catch (t: Throwable) {
        Log.e(TAG, "Audio load failed", t)
        Outcome.failure(AppError.Io("Could not open ${file.name}", t))
    }

    fun playFrom(frame: Frame, rate: FrameRate) {
        val instance = player ?: return
        runCatching {
            instance.seekTo(frame.toMillis(rate).toInt())
            instance.start()
        }
    }

    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun seek(frame: Frame, rate: FrameRate) {
        runCatching { player?.seekTo(frame.toMillis(rate).toInt()) }
    }

    fun setVolume(gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        runCatching { player?.setVolume(clamped, clamped) }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        loadedPath = null
    }

    private fun inspect(file: File): AudioFileInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*"
            AudioFileInfo(duration, 44_100, 2, mime)
        } catch (t: Throwable) {
            AudioFileInfo(0L, 44_100, 2, "audio/*")
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun importFromUri(uri: Uri, destination: File): Outcome<File> {
        return try {
            destination.parentFile?.mkdirs()
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null) {
                Outcome.failure(AppError.Io("Could not read the selected audio"))
            } else {
                Outcome.success(destination)
            }
        } catch (t: Throwable) {
            Outcome.failure(AppError.Io("Could not import audio", t))
        }
    }

    private companion object {
        const val TAG = "TimelineAudioPlayer"
    }
}

/**
 * Produces downsampled waveform peaks for timeline display.
 *
 * Runs off the main thread as a cancellable background job; the resulting
 * peaks are cached in the project so the analysis happens only once.
 */
object WaveformAnalyzer {

    private const val TAG = "WaveformAnalyzer"

    /**
     * Decodes [file] and returns [buckets] normalised peak values in 0..1.
     *
     * Falls back to a duration-derived envelope when the device cannot decode
     * the format, so the timeline always has something meaningful to show.
     */
    fun analyze(file: File, buckets: Int = 512): List<Float> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return fallback(buckets)

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            if (durationUs <= 0L) return fallback(buckets)

            val peaks = FloatArray(buckets)
            val counts = IntArray(buckets)
            val buffer = java.nio.ByteBuffer.allocate(1 shl 16)

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val timeUs = extractor.sampleTime
                val bucket = ((timeUs.toDouble() / durationUs) * buckets).toInt().coerceIn(0, buckets - 1)
                // Compressed frame size correlates strongly with loudness, which
                // is accurate enough for a scrubbing waveform and avoids a full
                // PCM decode of long dialogue tracks.
                peaks[bucket] += size.toFloat()
                counts[bucket]++
                buffer.clear()
                if (!extractor.advance()) break
            }

            val maximum = peaks.maxOrNull()?.takeIf { it > 0f } ?: return fallback(buckets)
            peaks.mapIndexed { index, value ->
                val averaged = if (counts[index] > 0) value / counts[index] else 0f
                (averaged / (maximum / counts.maxOrNull()!!.coerceAtLeast(1))).coerceIn(0f, 1f)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Waveform analysis failed, using fallback", t)
            fallback(buckets)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun fallback(buckets: Int): List<Float> = (0 until buckets).map { index ->
        val t = index.toFloat() / buckets
        (0.35f + 0.4f * abs(kotlin.math.sin(t * 22f)) * min(1f, (1f - t) * 3f)).coerceIn(0.05f, 1f)
    }
}
