package com.cartoonstudio.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cartoonstudio.data.projectstore.ProjectSummary
import com.cartoonstudio.feature.editor.EditorScreen
import com.cartoonstudio.feature.editor.EditorViewModel
import com.cartoonstudio.feature.onboarding.HomeScreen
import com.cartoonstudio.feature.settings.SettingsScreen
import com.cartoonstudio.platform.android.AppContainer
import java.io.File

/** Where the user currently is. */
sealed interface Destination {
    data object Home : Destination
    data class Editor(val projectId: String) : Destination
    data object Settings : Destination
}

/**
 * Minimal navigation state.
 *
 * The editor owns an expensive, long-lived session, so navigation is modelled
 * as explicit destinations rather than a back stack of recreated screens.
 */
class StudioNavigator {
    var destination by mutableStateOf<Destination>(Destination.Home)
        private set

    private var previous: Destination = Destination.Home

    fun go(target: Destination) {
        previous = destination
        destination = target
    }

    fun back() {
        destination = previous
        previous = Destination.Home
    }
}

@Composable
fun StudioApp(container: AppContainer, navigator: StudioNavigator) {
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val summaries by appViewModel.summaries.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val loaded by appViewModel.loadedProject.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { appViewModel.refresh() }

    // Thumbnails are decoded lazily and memoised per (project, modified time),
    // so scrolling the project grid never re-reads the disk.
    val thumbnails = remember { mutableMapOf<String, ImageBitmap?>() }

    when (val destination = navigator.destination) {
        Destination.Home -> HomeScreen(
            projects = summaries,
            thumbnailLoader = { summary ->
                thumbnails.getOrPut("${summary.projectId}@${summary.modifiedAtMillis}") {
                    decodeThumbnail(summary)
                }
            },
            onOpenProject = { projectId ->
                appViewModel.open(projectId, recoverSnapshot = false)
                navigator.go(Destination.Editor(projectId))
            },
            onCreateProject = { request ->
                appViewModel.create(request) { project ->
                    navigator.go(Destination.Editor(project.id))
                }
            },
            onDeleteProject = appViewModel::delete,
            onDuplicateProject = appViewModel::duplicate,
            onRecoverProject = { projectId ->
                appViewModel.open(projectId, recoverSnapshot = true)
                navigator.go(Destination.Editor(projectId))
            },
            onOpenSettings = { navigator.go(Destination.Settings) },
        )

        is Destination.Editor -> {
            val project = loaded
            if (project == null || project.id != destination.projectId) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val editorViewModel: EditorViewModel = viewModel(
                    key = "editor-${project.id}-${project.revision}",
                    factory = EditorViewModel.factory(container, project),
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    onExit = {
                        appViewModel.refresh()
                        appViewModel.close()
                        navigator.go(Destination.Home)
                    },
                    onOpenSettings = { navigator.go(Destination.Settings) },
                )
            }
        }

        Destination.Settings -> SettingsScreen(
            settings = settings,
            appVersion = BuildConfig.VERSION_NAME,
            onSettingsChange = appViewModel::updateSettings,
            onBack = navigator::back,
        )
    }
}

/** Decodes a project thumbnail from disk, or null when there is none yet. */
private fun decodeThumbnail(summary: ProjectSummary): ImageBitmap? {
    val path = summary.thumbnailPath ?: return null
    if (!File(path).exists()) return null
    return runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}
