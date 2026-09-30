package com.cartoonstudio.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import com.cartoonstudio.data.preferences.EditorSettings
import com.cartoonstudio.data.projectstore.ProjectSummary
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.feature.onboarding.NewProjectRequest
import com.cartoonstudio.platform.android.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns everything outside an editing session: the project list, preferences
 * and the currently opened document.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val summaries: StateFlow<List<ProjectSummary>> = container.projectRepository.summaries

    val settings: StateFlow<EditorSettings> = container.preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorSettings(),
    )

    private val openProject = MutableStateFlow<Project?>(null)
    val loadedProject: StateFlow<Project?> = openProject.asStateFlow()

    fun refresh() {
        viewModelScope.launch { container.projectRepository.refresh() }
    }

    fun open(projectId: String, recoverSnapshot: Boolean) {
        viewModelScope.launch {
            if (!recoverSnapshot) container.projectRepository.discardSnapshot(projectId)
            when (val loaded = container.projectRepository.load(projectId)) {
                is Outcome.Success -> {
                    openProject.value = loaded.value
                    container.preferences.setLastProject(projectId)
                }

                is Outcome.Failure ->
                    Log.e(TAG, "Could not open $projectId: ${loaded.error.message}")
            }
        }
    }

    fun close() {
        openProject.value = null
    }

    fun create(request: NewProjectRequest, onCreated: (Project) -> Unit) {
        viewModelScope.launch {
            container.projectRepository.create(request.name, request.settings, request.template)
                .onSuccess { project ->
                    openProject.value = project
                    onCreated(project)
                }
                .onFailure { Log.e(TAG, "Could not create project: ${it.message}") }
        }
    }

    fun delete(projectId: String) {
        viewModelScope.launch {
            container.projectRepository.delete(projectId)
            container.projectRepository.refresh()
        }
    }

    fun duplicate(projectId: String) {
        viewModelScope.launch {
            container.projectRepository.duplicate(projectId)
            container.projectRepository.refresh()
        }
    }

    fun updateSettings(updated: EditorSettings) {
        val current = settings.value
        viewModelScope.launch {
            with(container.preferences) {
                if (updated.themeMode != current.themeMode) setThemeMode(updated.themeMode)
                if (updated.autosaveEnabled != current.autosaveEnabled) {
                    setAutosaveEnabled(updated.autosaveEnabled)
                }
                if (updated.autosaveIntervalSeconds != current.autosaveIntervalSeconds) {
                    setAutosaveInterval(updated.autosaveIntervalSeconds)
                }
                if (updated.onionSkinEnabled != current.onionSkinEnabled) {
                    setOnionSkinEnabled(updated.onionSkinEnabled)
                }
                if (updated.onionFramesBefore != current.onionFramesBefore ||
                    updated.onionFramesAfter != current.onionFramesAfter
                ) {
                    setOnionFrames(updated.onionFramesBefore, updated.onionFramesAfter)
                }
                if (updated.showPerformanceHud != current.showPerformanceHud) {
                    setPerformanceHud(updated.showPerformanceHud)
                }
                if (updated.stylusOnlyDrawing != current.stylusOnlyDrawing) {
                    setStylusOnly(updated.stylusOnlyDrawing)
                }
                if (updated.leftHandedUi != current.leftHandedUi) setLeftHanded(updated.leftHandedUi)
                if (updated.snapToGrid != current.snapToGrid) setSnapToGrid(updated.snapToGrid)
                if (updated.defaultFrameRate != current.defaultFrameRate) {
                    setDefaultFrameRate(updated.defaultFrameRate)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppViewModel"

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(container) as T
            }
    }
}
