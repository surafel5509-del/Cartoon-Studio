package com.cartoonstudio.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.cartoonstudio.platform.android.AppContainer

/**
 * Process entry point.
 *
 * The dependency graph is created eagerly here (it is cheap — everything
 * inside is lazy) so the first screen never has to wait on wiring, and memory
 * pressure callbacks have somewhere to land.
 */
class CartoonStudioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.onLowMemory()
        }
    }

    @Deprecated("Deprecated in Android 14 but still delivered on older devices")
    override fun onLowMemory() {
        super.onLowMemory()
        container.onLowMemory()
    }
}
