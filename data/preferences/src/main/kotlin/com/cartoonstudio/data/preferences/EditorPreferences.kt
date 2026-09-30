package com.cartoonstudio.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cartoon_studio_settings")

/** User settings that are not part of any project document. */
data class EditorSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val autosaveEnabled: Boolean = true,
    val autosaveIntervalSeconds: Int = 20,
    val onionSkinEnabled: Boolean = false,
    val onionFramesBefore: Int = 2,
    val onionFramesAfter: Int = 1,
    val showPerformanceHud: Boolean = false,
    val stylusOnlyDrawing: Boolean = false,
    val leftHandedUi: Boolean = false,
    val snapToGrid: Boolean = false,
    val defaultFrameRate: Int = 24,
    val hasCompletedOnboarding: Boolean = false,
    val lastProjectId: String? = null,
    val favoriteAssetIds: Set<String> = emptySet(),
)

enum class ThemeMode { System, Light, Dark }

/**
 * Persistent user settings backed by DataStore.
 *
 * Preferences are deliberately separate from project documents: moving a
 * project between devices must never carry UI state with it.
 */
class EditorPreferences(private val context: Context) {

    val settings: Flow<EditorSettings> = context.dataStore.data.map { prefs ->
        EditorSettings(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            autosaveEnabled = prefs[Keys.AUTOSAVE] ?: true,
            autosaveIntervalSeconds = prefs[Keys.AUTOSAVE_INTERVAL] ?: 20,
            onionSkinEnabled = prefs[Keys.ONION] ?: false,
            onionFramesBefore = prefs[Keys.ONION_BEFORE] ?: 2,
            onionFramesAfter = prefs[Keys.ONION_AFTER] ?: 1,
            showPerformanceHud = prefs[Keys.HUD] ?: false,
            stylusOnlyDrawing = prefs[Keys.STYLUS_ONLY] ?: false,
            leftHandedUi = prefs[Keys.LEFT_HANDED] ?: false,
            snapToGrid = prefs[Keys.SNAP] ?: false,
            defaultFrameRate = prefs[Keys.FRAME_RATE] ?: 24,
            hasCompletedOnboarding = prefs[Keys.ONBOARDED] ?: false,
            lastProjectId = prefs[Keys.LAST_PROJECT],
            favoriteAssetIds = prefs[Keys.FAVORITES] ?: emptySet(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = put { it[Keys.THEME] = mode.name }
    suspend fun setAutosaveEnabled(enabled: Boolean) = put { it[Keys.AUTOSAVE] = enabled }
    suspend fun setAutosaveInterval(seconds: Int) = put { it[Keys.AUTOSAVE_INTERVAL] = seconds.coerceIn(5, 300) }
    suspend fun setOnionSkinEnabled(enabled: Boolean) = put { it[Keys.ONION] = enabled }
    suspend fun setOnionFrames(before: Int, after: Int) = put {
        it[Keys.ONION_BEFORE] = before.coerceIn(0, 8)
        it[Keys.ONION_AFTER] = after.coerceIn(0, 8)
    }
    suspend fun setPerformanceHud(enabled: Boolean) = put { it[Keys.HUD] = enabled }
    suspend fun setStylusOnly(enabled: Boolean) = put { it[Keys.STYLUS_ONLY] = enabled }
    suspend fun setLeftHanded(enabled: Boolean) = put { it[Keys.LEFT_HANDED] = enabled }
    suspend fun setSnapToGrid(enabled: Boolean) = put { it[Keys.SNAP] = enabled }
    suspend fun setDefaultFrameRate(fps: Int) = put { it[Keys.FRAME_RATE] = fps.coerceIn(6, 60) }
    suspend fun setOnboardingComplete(complete: Boolean) = put { it[Keys.ONBOARDED] = complete }
    suspend fun setLastProject(projectId: String?) = put {
        if (projectId == null) it.remove(Keys.LAST_PROJECT) else it[Keys.LAST_PROJECT] = projectId
    }
    suspend fun setFavorites(ids: Set<String>) = put { it[Keys.FAVORITES] = ids }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val AUTOSAVE = booleanPreferencesKey("autosave_enabled")
        val AUTOSAVE_INTERVAL = intPreferencesKey("autosave_interval")
        val ONION = booleanPreferencesKey("onion_enabled")
        val ONION_BEFORE = intPreferencesKey("onion_before")
        val ONION_AFTER = intPreferencesKey("onion_after")
        val HUD = booleanPreferencesKey("performance_hud")
        val STYLUS_ONLY = booleanPreferencesKey("stylus_only")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val SNAP = booleanPreferencesKey("snap_to_grid")
        val FRAME_RATE = intPreferencesKey("default_frame_rate")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val LAST_PROJECT = stringPreferencesKey("last_project")
        val FAVORITES = stringSetPreferencesKey("favorite_assets")
    }
}
