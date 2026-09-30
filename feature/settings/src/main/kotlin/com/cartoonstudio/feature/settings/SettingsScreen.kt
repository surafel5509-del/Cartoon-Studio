package com.cartoonstudio.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cartoonstudio.data.preferences.EditorSettings
import com.cartoonstudio.data.preferences.ThemeMode
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.designsystem.spacing

/** Every preference the studio exposes, grouped by concern. */
@Composable
fun SettingsScreen(
    settings: EditorSettings,
    appVersion: String,
    onSettingsChange: (EditorSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            OptionRow(
                options = ThemeMode.entries.toList(),
                selected = settings.themeMode,
                onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
                labelOf = { it.name },
            )
            SettingToggle(
                "Left-handed layout",
                "Mirrors the tool rail to the right edge",
                settings.leftHandedUi,
            ) { onSettingsChange(settings.copy(leftHandedUi = it)) }

            SectionHeader("Drawing")
            SettingToggle(
                "Stylus only",
                "Ignore finger input while drawing so you can rest your hand",
                settings.stylusOnlyDrawing,
            ) { onSettingsChange(settings.copy(stylusOnlyDrawing = it)) }
            SettingToggle("Snap to grid", "Align strokes and layers to the grid", settings.snapToGrid) {
                onSettingsChange(settings.copy(snapToGrid = it))
            }

            SectionHeader("Onion Skin")
            SettingToggle("Enabled by default", "", settings.onionSkinEnabled) {
                onSettingsChange(settings.copy(onionSkinEnabled = it))
            }
            SliderRow(
                label = "Frames before",
                value = settings.onionFramesBefore.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(onionFramesBefore = it.toInt())) },
                valueRange = 0f..5f,
                steps = 4,
                valueFormatter = { it.toInt().toString() },
            )
            SliderRow(
                label = "Frames after",
                value = settings.onionFramesAfter.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(onionFramesAfter = it.toInt())) },
                valueRange = 0f..5f,
                steps = 4,
                valueFormatter = { it.toInt().toString() },
            )

            SectionHeader("Playback")
            SliderRow(
                label = "Default frame rate",
                value = settings.defaultFrameRate.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(defaultFrameRate = it.toInt())) },
                valueRange = 6f..60f,
                valueFormatter = { "${it.toInt()} fps" },
            )

            SectionHeader("Saving")
            SettingToggle(
                "Autosave",
                "Writes a recovery snapshot while you work",
                settings.autosaveEnabled,
            ) { onSettingsChange(settings.copy(autosaveEnabled = it)) }
            SliderRow(
                label = "Autosave interval",
                value = settings.autosaveIntervalSeconds.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(autosaveIntervalSeconds = it.toInt())) },
                valueRange = 5f..300f,
                valueFormatter = { "${it.toInt()}s" },
            )

            SectionHeader("Diagnostics")
            SettingToggle(
                "Performance overlay",
                "Shows frame time, draw calls and memory on the canvas",
                settings.showPerformanceHud,
            ) { onSettingsChange(settings.copy(showPerformanceHud = it)) }

            SectionHeader("About")
            Text(
                "Cartoon Studio $appVersion\nAll projects stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotEmpty()) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
