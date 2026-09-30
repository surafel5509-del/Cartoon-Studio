package com.cartoonstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.cartoonstudio.data.preferences.ThemeMode
import com.cartoonstudio.platform.android.AppContainer
import com.cartoonstudio.ui.designsystem.CartoonStudioTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.cartoonstudio.data.preferences.EditorSettings
import androidx.lifecycle.lifecycleScope

/**
 * The single activity.
 *
 * Everything above this is Compose: one activity keeps the canvas, timeline
 * and playback clock alive across navigation, which matters because
 * re-creating the render pipeline mid-session would drop frames.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = AppContainer.get(this)
        val settingsFlow = container.preferences.settings.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = EditorSettings(),
        )

        setContent {
            val settings by settingsFlow.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CartoonStudioTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navigator = remember { StudioNavigator() }
                    StudioApp(container = container, navigator = navigator)
                }
            }
        }
    }
}
