package com.cartoonstudio.feature.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.core.time.Timecode
import com.cartoonstudio.domain.audio.AudioAsset
import com.cartoonstudio.domain.audio.AudioClip
import com.cartoonstudio.domain.audio.AudioRole
import com.cartoonstudio.domain.audio.AudioTrack
import com.cartoonstudio.domain.audio.LipSyncTrack
import com.cartoonstudio.ui.components.EmptyState
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.designsystem.spacing

/** Audio editing operations surfaced by the panel. */
data class AudioCallbacks(
    val onImport: () -> Unit = {},
    val onAddTrack: (AudioRole) -> Unit = {},
    val onToggleMute: (String) -> Unit = {},
    val onToggleSolo: (String) -> Unit = {},
    val onRemoveTrack: (String) -> Unit = {},
    val onUpdateTrack: (AudioTrack) -> Unit = {},
    val onSelectClip: (String) -> Unit = {},
    val onSeek: (Frame) -> Unit = {},
    val onGenerateLipSync: (String) -> Unit = {},
)

/**
 * Audio track panel with waveform display.
 *
 * Waveform peaks are pre-computed once on import (see `WaveformAnalyzer`) and
 * stored on the asset, so scrolling the timeline never decodes audio.
 */
@Composable
fun AudioPanel(
    tracks: List<AudioTrack>,
    assets: Map<String, AudioAsset>,
    lipSyncTracks: List<LipSyncTrack>,
    currentFrame: Frame,
    frameRate: FrameRate,
    durationFrames: Int,
    callbacks: AudioCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader("Audio · ${tracks.size} tracks", modifier = Modifier.weight(1f))
            IconButton(onClick = callbacks.onImport) {
                Icon(Icons.Filled.Add, contentDescription = "Import audio")
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.GraphicEq,
                title = "No audio yet",
                message = "Import dialogue, music or effects to sync your animation to sound.",
            )
            return@Column
        }

        Text(
            Timecode.format(currentFrame, frameRate),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tracks.size) { index ->
                val track = tracks[index]
                AudioTrackRow(
                    track = track,
                    assets = assets,
                    currentFrame = currentFrame,
                    durationFrames = durationFrames,
                    hasLipSync = lipSyncTracks.any { sync ->
                        track.clips.any { it.id == sync.audioClipId }
                    },
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    track: AudioTrack,
    assets: Map<String, AudioAsset>,
    currentFrame: Frame,
    durationFrames: Int,
    hasLipSync: Boolean,
    callbacks: AudioCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.tiny),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { callbacks.onToggleMute(track.id) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (track.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Mute",
                    modifier = Modifier.size(17.dp),
                    tint = if (track.muted) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${track.role.name} · ${track.clips.size} clips",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (track.role == AudioRole.Dialogue) {
                IconButton(
                    onClick = { track.clips.firstOrNull()?.let { callbacks.onGenerateLipSync(it.id) } },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = "Auto lip sync",
                        modifier = Modifier.size(17.dp),
                        tint = if (hasLipSync) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (track.solo) "SOLO" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable { callbacks.onToggleSolo(track.id) },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            WaveformStrip(
                clips = track.clips,
                assets = assets,
                currentFrame = currentFrame,
                durationFrames = durationFrames,
                muted = track.muted,
            )
        }

        SliderRow(
            label = "Gain",
            value = track.gain,
            onValueChange = { callbacks.onUpdateTrack(track.copy(gain = it)) },
            valueRange = 0f..2f,
            valueFormatter = { "%.2f".format(it) },
        )
    }
}

/** Draws every clip on a track as a mirrored peak envelope. */
@Composable
private fun WaveformStrip(
    clips: List<AudioClip>,
    assets: Map<String, AudioAsset>,
    currentFrame: Frame,
    durationFrames: Int,
    muted: Boolean,
) {
    val waveColor = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary
    val clipColor = waveColor.copy(alpha = 0.18f)
    val playheadColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frames = durationFrames.coerceAtLeast(1).toFloat()
        val pxPerFrame = size.width / frames
        val midY = size.height / 2f

        clips.forEach { clip ->
            val startX = clip.startFrame.index * pxPerFrame
            val widthPx = clip.lengthInFrames * pxPerFrame
            drawRect(
                color = clipColor,
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(widthPx, size.height),
            )

            val peaks = assets[clip.assetId]?.waveformPeaks.orEmpty()
            if (peaks.isEmpty() || widthPx <= 1f) return@forEach

            val columns = widthPx.toInt().coerceIn(1, 4096)
            for (column in 0 until columns) {
                val peak = peaks[(column * peaks.size / columns).coerceIn(0, peaks.lastIndex)]
                val half = (peak.coerceIn(0f, 1f) * midY * 0.92f)
                val x = startX + column
                drawLine(
                    color = waveColor,
                    start = Offset(x, midY - half),
                    end = Offset(x, midY + half),
                    strokeWidth = 1f,
                )
            }
        }

        val playheadX = currentFrame.index * pxPerFrame
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 2f,
        )
    }
}

/** Track creation shelf, one entry per audio role. */
@Composable
fun AddAudioTrackRow(onAddTrack: (AudioRole) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("New Track")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            AudioRole.entries.forEach { role ->
                Text(
                    role.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(Color.Transparent)
                        .clickable { onAddTrack(role) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}
