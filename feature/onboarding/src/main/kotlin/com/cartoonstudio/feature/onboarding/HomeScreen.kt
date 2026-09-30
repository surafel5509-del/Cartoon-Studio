package com.cartoonstudio.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cartoonstudio.data.projectstore.ProjectSummary
import com.cartoonstudio.domain.model.CanvasPreset
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.ProjectTemplate
import com.cartoonstudio.ui.components.EmptyState
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.designsystem.spacing

/** A project the user asked to create. */
data class NewProjectRequest(
    val name: String,
    val settings: ProjectSettings,
    val template: ProjectTemplate,
)

/**
 * Project browser — the app's launch surface.
 *
 * Recovery is surfaced here rather than inside the editor: a crashed session
 * is offered back the moment the user sees the project again.
 */
@Composable
fun HomeScreen(
    projects: List<ProjectSummary>,
    thumbnailLoader: (ProjectSummary) -> ImageBitmap?,
    onOpenProject: (String) -> Unit,
    onCreateProject: (NewProjectRequest) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRecoverProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Project") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.medium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cartoon Studio", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Draw, animate and export — everything on device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            if (projects.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Movie,
                    title = "No projects yet",
                    message = "Start with a blank canvas or pick a template to block out a short film.",
                    action = {
                        Button(onClick = { showCreateDialog = true }) { Text("Create your first project") }
                    },
                )
            } else {
                SectionHeader("Projects · ${projects.size}")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(200.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        MaterialTheme.spacing.large,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(projects.size) { index ->
                        val summary = projects[index]
                        ProjectCard(
                            summary = summary,
                            thumbnail = thumbnailLoader(summary),
                            onOpen = { onOpenProject(summary.projectId) },
                            onDuplicate = { onDuplicateProject(summary.projectId) },
                            onDelete = { pendingDelete = summary },
                            onRecover = { onRecoverProject(summary.projectId) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NewProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = {
                showCreateDialog = false
                onCreateProject(it)
            },
        )
    }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${summary.name}?") },
            text = { Text("This permanently removes the project and all of its artwork from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(summary.projectId)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(
    summary: ProjectSummary,
    thumbnail: ImageBitmap?,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRecover: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(summary.aspectRatio.coerceIn(0.4f, 2.5f))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail,
                        contentDescription = summary.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (summary.needsRecovery) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MaterialTheme.spacing.small)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(onClick = onRecover)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiary,
                        )
                        Text(
                            " Recover",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.tiny,
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.small,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${summary.sceneCount} shots · ${summary.durationFrames}f · " +
                            "${summary.canvasWidth}×${summary.canvasHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Duplicate", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete, "Delete",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (NewProjectRequest) -> Unit,
) {
    var name by remember { mutableStateOf("Untitled") }
    var preset by remember { mutableStateOf(CanvasPreset.presets.first()) }
    var template by remember { mutableStateOf(ProjectTemplate.Blank) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionHeader("Canvas")
                OptionRow(
                    options = CanvasPreset.presets,
                    selected = preset,
                    onSelect = { preset = it },
                    labelOf = { it.name },
                )
                Text(
                    preset.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
                SectionHeader("Template")
                OptionRow(
                    options = ProjectTemplate.entries.toList(),
                    selected = template,
                    onSelect = { template = it },
                    labelOf = { it.displayName },
                )
                Text(
                    template.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        NewProjectRequest(
                            name = name.ifBlank { "Untitled" },
                            settings = preset.settings,
                            template = template,
                        ),
                    )
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
