package com.cartoonstudio.feature.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cartoonstudio.domain.export.ExportFormat
import com.cartoonstudio.domain.export.ExportPreset
import com.cartoonstudio.domain.export.ExportQuality
import com.cartoonstudio.domain.export.ExportResult
import com.cartoonstudio.domain.export.ExportScope
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.engine.export.ExportPlanner
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.designsystem.spacing

/** Progress of a running or finished export. */
sealed interface ExportProgress {
    data object Idle : ExportProgress
    data class Running(val fraction: Float, val message: String) : ExportProgress
    data class Finished(val result: ExportResult) : ExportProgress
    data class Failed(val message: String) : ExportProgress
}

/**
 * Export configuration screen.
 *
 * The estimate shown here comes from the same [ExportPlanner] the renderer
 * uses, so the frame count and duration always match the real output.
 */
@Composable
fun ExportScreen(
    project: Project,
    settings: ExportSettings,
    progress: ExportProgress,
    onSettingsChange: (ExportSettings) -> Unit,
    onStartExport: () -> Unit,
    onCancel: () -> Unit,
    onShare: (ExportResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = remember(project, settings) { ExportPlanner.plan(project, settings) }
    val validation = settings.validate()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Export", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Presets")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                ExportPreset.presets.forEach { preset ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSettingsChange(
                                    preset.settings.copy(
                                        scope = settings.scope,
                                        fileNameStem = settings.fileNameStem,
                                        frameRate = project.settings.frameRate,
                                    ),
                                )
                            },
                    ) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                preset.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader("Format")
            OptionRow(
                options = ExportFormat.entries.toList(),
                selected = settings.format,
                onSelect = { onSettingsChange(settings.copy(format = it)) },
                labelOf = { it.displayName },
            )

            SectionHeader("Quality")
            OptionRow(
                options = ExportQuality.entries.toList(),
                selected = settings.quality,
                onSelect = { onSettingsChange(settings.copy(quality = it)) },
                labelOf = { it.displayName },
            )

            SectionHeader("Range")
            OptionRow(
                options = buildList {
                    add(ExportScope.WholeProject as ExportScope)
                    project.scenes.forEach { add(ExportScope.SingleScene(it.id)) }
                },
                selected = settings.scope,
                onSelect = { onSettingsChange(settings.copy(scope = it)) },
                labelOf = { scope ->
                    when (scope) {
                        is ExportScope.WholeProject -> "Whole film"
                        is ExportScope.SingleScene ->
                            project.scene(scope.sceneId)?.name ?: "Shot"
                        is ExportScope.SceneRange ->
                            "${project.scene(scope.sceneId)?.name ?: "Shot"} range"
                    }
                },
            )

            SliderRow(
                label = "Resolution",
                value = settings.resolutionScale,
                onValueChange = { onSettingsChange(settings.copy(resolutionScale = it)) },
                valueRange = 0.25f..2f,
                valueFormatter = {
                    val scaled = settings.copy(resolutionScale = it)
                    "${scaled.outputWidth(project.settings)}×${scaled.outputHeight(project.settings)}"
                },
            )
            SliderRow(
                label = "Frame step",
                value = settings.frameStep.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(frameStep = it.toInt().coerceAtLeast(1))) },
                valueRange = 1f..4f,
                steps = 2,
                valueFormatter = { if (it.toInt() == 1) "Every frame" else "On ${it.toInt()}s" },
            )

            ToggleRow(
                label = "Transparent background",
                checked = settings.transparentBackground,
                enabled = settings.format.supportsAlpha,
                onCheckedChange = { onSettingsChange(settings.copy(transparentBackground = it)) },
            )
            ToggleRow(
                label = "Include audio",
                checked = settings.includeAudio,
                enabled = settings.format.isVideo,
                onCheckedChange = { onSettingsChange(settings.copy(includeAudio = it)) },
            )

            OutlinedTextField(
                value = settings.fileNameStem,
                onValueChange = { onSettingsChange(settings.copy(fileNameStem = it)) },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
            )

            SectionHeader("Summary")
            Text(
                "${plan.frameCount} frames · ${plan.widthPixels}×${plan.heightPixels} · " +
                    "${plan.durationMillis / 1000f}s · ${settings.format.extension.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            )
            if (settings.format.isVideo) {
                Text(
                    "Bitrate ≈ ${settings.bitrate(project.settings) / 1_000_000f} Mbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
            }
        }

        ExportFooter(
            progress = progress,
            validation = validation,
            onStartExport = onStartExport,
            onCancel = onCancel,
            onShare = onShare,
        )
    }
}

@Composable
private fun ExportFooter(
    progress: ExportProgress,
    validation: String?,
    onStartExport: () -> Unit,
    onCancel: () -> Unit,
    onShare: (ExportResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        when (progress) {
            is ExportProgress.Running -> {
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(progress.message, style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            is ExportProgress.Finished -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "  Saved ${progress.result.frameCount} frames to ${progress.result.outputPath}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Button(onClick = { onShare(progress.result) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Share")
                }
            }

            is ExportProgress.Failed -> {
                Text(
                    progress.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onStartExport, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }

            ExportProgress.Idle -> {
                validation?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onStartExport,
                    enabled = validation == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Text("  Export", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
