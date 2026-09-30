package com.cartoonstudio.data.projectstore

import android.content.Context
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.DispatcherProvider
import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import com.cartoonstudio.core.common.runCatchingOutcome
import com.cartoonstudio.core.serialization.Compatibility
import com.cartoonstudio.core.serialization.MigrationPipeline
import com.cartoonstudio.core.serialization.SchemaVersion
import com.cartoonstudio.core.serialization.StudioJson
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.domain.model.ProjectFactory
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.ProjectTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Crash-safe project persistence.
 *
 * Guarantees:
 * - saves are atomic, so a kill mid-write never corrupts a project,
 * - the project list is served from cheap manifests, not full documents,
 * - a damaged document falls back to the autosave snapshot before failing,
 * - schema versions are checked and migrated explicitly.
 */
class ProjectRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val migrations: MigrationPipeline = MigrationPipeline.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val rootDirectory: File by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }
    }

    private val summariesState = MutableStateFlow<List<ProjectSummary>>(emptyList())

    /** Observable project list for the home screen. */
    val summaries: StateFlow<List<ProjectSummary>> = summariesState.asStateFlow()

    fun pathsFor(projectId: String) = ProjectPaths(File(rootDirectory, projectId))

    suspend fun refresh(): List<ProjectSummary> = withContext(dispatchers.io) {
        val found = rootDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { directory ->
                val paths = ProjectPaths(directory)
                readManifest(paths).getOrNull()?.let { ProjectSummary.from(it, paths) }
            }
            ?.sortedByDescending { it.modifiedAtMillis }
            .orEmpty()
        summariesState.value = found
        found
    }

    suspend fun create(
        name: String,
        settings: ProjectSettings = ProjectSettings.HD,
        template: ProjectTemplate = ProjectTemplate.Blank,
    ): Outcome<Project> = withContext(dispatchers.io) {
        val project = ProjectFactory.createProject(name, settings, template, clock())
        save(project).map { project }.also { refresh() }
    }

    suspend fun load(projectId: String): Outcome<Project> = withContext(dispatchers.io) {
        val paths = pathsFor(projectId)
        if (!paths.root.exists()) return@withContext Outcome.failure(AppError.NotFound("Project"))

        val manifestOutcome = readManifest(paths)
        val manifest = manifestOutcome.getOrNull()
        if (manifest != null) {
            when (val compatibility = Compatibility.of(manifest.schemaVersion)) {
                is Compatibility.TooNew -> return@withContext Outcome.failure(
                    AppError.Unsupported("Project made with a newer version (${compatibility.found})")
                )
                is Compatibility.TooOld -> return@withContext Outcome.failure(
                    AppError.Unsupported("Project format ${compatibility.found} is no longer supported")
                )
                else -> Unit
            }
        }

        val primary = readDocument(paths.document, manifest?.schemaVersion)
        if (primary.isSuccess) return@withContext primary

        // The main document is damaged — try the autosave snapshot before
        // reporting failure, exactly as the recovery strategy requires.
        Log.w("ProjectRepository", "project.json unreadable for $projectId, trying autosave")
        val recovered = readDocument(paths.autosave, manifest?.schemaVersion)
        if (recovered.isSuccess) return@withContext recovered

        Outcome.failure(AppError.Corrupt("Project ${manifest?.name ?: projectId}"))
    }

    suspend fun save(project: Project): Outcome<Unit> = withContext(dispatchers.io) {
        val paths = pathsFor(project.id)
        paths.ensureDirectories()
        val stamped = project.copy(modifiedAtMillis = clock())

        runCatchingOutcome("Encoding project") {
            StudioJson.pretty.encodeToString(Project.serializer(), stamped)
        }.flatMap { json ->
            AtomicFiles.writeText(paths.document, json)
        }.flatMap {
            writeManifest(paths, manifestFor(stamped, dirty = false))
        }.onSuccess {
            // A successful full save makes the autosave snapshot redundant.
            paths.recovery.delete()
            refresh()
        }
    }

    /**
     * Fast, frequent snapshot written to a separate file.
     *
     * Autosave never touches `project.json`, so an interrupted autosave can
     * never damage the user's last explicit save.
     */
    suspend fun autosave(project: Project): Outcome<Unit> = withContext(dispatchers.io) {
        val paths = pathsFor(project.id)
        paths.ensureDirectories()
        runCatchingOutcome("Encoding autosave") {
            StudioJson.compact.encodeToString(Project.serializer(), project)
        }.flatMap { json -> AtomicFiles.writeText(paths.autosave, json) }
    }

    /** True when an autosave snapshot is newer than the last explicit save. */
    suspend fun hasRecoverableSnapshot(projectId: String): Boolean = withContext(dispatchers.io) {
        val paths = pathsFor(projectId)
        paths.autosave.exists() &&
            paths.autosave.lastModified() > paths.document.lastModified() + RECOVERY_TOLERANCE_MS
    }

    suspend fun discardSnapshot(projectId: String) = withContext(dispatchers.io) {
        pathsFor(projectId).autosave.delete()
        Unit
    }

    suspend fun delete(projectId: String): Outcome<Unit> = withContext(dispatchers.io) {
        AtomicFiles.deleteRecursively(pathsFor(projectId).root).onSuccess { refresh() }
    }

    suspend fun duplicate(projectId: String): Outcome<Project> = withContext(dispatchers.io) {
        load(projectId).flatMap { original ->
            val copy = original.copy(
                id = Ids.next("proj"),
                name = "${original.name} copy",
                createdAtMillis = clock(),
                modifiedAtMillis = clock(),
                revision = 0,
            )
            save(copy).map { copy }
        }
    }

    suspend fun rename(projectId: String, newName: String): Outcome<Unit> =
        load(projectId).flatMap { save(it.copy(name = newName)) }

    /** Writes the cover image used in the project browser. */
    suspend fun writeThumbnail(projectId: String, bytes: ByteArray): Outcome<Unit> =
        withContext(dispatchers.io) {
            val paths = pathsFor(projectId)
            paths.ensureDirectories()
            AtomicFiles.write(paths.thumbnail, bytes)
        }

    fun audioDirectory(projectId: String): File = pathsFor(projectId).audio.apply { mkdirs() }

    fun assetDirectory(projectId: String): File = pathsFor(projectId).assets.apply { mkdirs() }

    private fun readManifest(paths: ProjectPaths): Outcome<ProjectManifest> =
        AtomicFiles.readText(paths.manifest).flatMap { text ->
            runCatchingOutcome("Reading manifest") {
                StudioJson.pretty.decodeFromString(ProjectManifest.serializer(), text)
            }
        }

    private fun writeManifest(paths: ProjectPaths, manifest: ProjectManifest): Outcome<Unit> =
        runCatchingOutcome("Encoding manifest") {
            StudioJson.pretty.encodeToString(ProjectManifest.serializer(), manifest)
        }.flatMap { AtomicFiles.writeText(paths.manifest, it) }

    private fun readDocument(file: File, version: SchemaVersion?): Outcome<Project> =
        AtomicFiles.readText(file).flatMap { text ->
            runCatchingOutcome("Reading project") {
                val element = StudioJson.pretty.parseToJsonElement(text)
                val migrated = if (version != null && version < SchemaVersion.CURRENT) {
                    migrations.migrate(element as JsonObject, version)
                } else {
                    element
                }
                StudioJson.pretty.decodeFromJsonElement(Project.serializer(), migrated)
            }
        }

    private fun manifestFor(project: Project, dirty: Boolean) = ProjectManifest(
        projectId = project.id,
        name = project.name,
        schemaVersion = SchemaVersion.CURRENT,
        createdAtMillis = project.createdAtMillis,
        modifiedAtMillis = project.modifiedAtMillis,
        revision = project.revision,
        sceneCount = project.scenes.size,
        layerCount = project.layerCount,
        durationFrames = project.totalFrames,
        canvasWidth = project.settings.canvasWidth,
        canvasHeight = project.settings.canvasHeight,
        frameRateFps = project.settings.frameRate.fps,
        description = project.description,
        dirtyShutdown = dirty,
    )

    private companion object {
        const val RECOVERY_TOLERANCE_MS = 1_500L
    }
}
