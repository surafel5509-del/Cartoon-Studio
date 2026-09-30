package com.cartoonstudio.platform.android

import android.content.Context
import android.util.Log as AndroidLog
import com.cartoonstudio.core.common.DefaultDispatcherProvider
import com.cartoonstudio.core.common.DispatcherProvider
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.LogLevel
import com.cartoonstudio.core.common.LogSink
import com.cartoonstudio.data.assetstore.AnimationLibrary
import com.cartoonstudio.data.assetstore.AssetRepository
import com.cartoonstudio.data.cache.BoundedCache
import com.cartoonstudio.data.preferences.EditorPreferences
import com.cartoonstudio.data.projectstore.ProjectRepository
import com.cartoonstudio.platform.graphics.SceneFrameRenderer

/**
 * Composition root.
 *
 * Dependencies are wired explicitly rather than through an annotation
 * processor: the graph is small, the wiring is readable, and builds stay fast
 * because there is no code generation step in the critical path.
 */
class AppContainer private constructor(
    private val context: Context,
    val dispatchers: DispatcherProvider,
) {

    val preferences: EditorPreferences by lazy { EditorPreferences(context) }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(context, dispatchers)
    }

    val assetRepository: AssetRepository by lazy { AssetRepository() }

    val frameRenderer: SceneFrameRenderer by lazy {
        SceneFrameRenderer(clipResolver = AnimationLibrary::resolve)
    }

    val exportEngine: ExportEngine by lazy {
        ExportEngine(context, dispatchers, projectRepository)
    }

    /** Bounded thumbnail cache; never required to open a project. */
    val thumbnailCache: BoundedCache<String, android.graphics.Bitmap> by lazy {
        BoundedCache(
            maxBytes = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024),
            sizeOf = { it.allocationByteCount.toLong() },
            onEvicted = { _, bitmap -> if (!bitmap.isRecycled) bitmap.recycle() },
        )
    }

    fun onLowMemory() {
        thumbnailCache.trimToHalf()
        Log.w(TAG, "Low memory: trimmed caches")
    }

    companion object {
        private const val TAG = "AppContainer"

        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(
                context.applicationContext,
                DefaultDispatcherProvider,
            ).also {
                Log.install(AndroidLogSink)
                instance = it
            }
        }
    }
}

/** Routes engine logging to logcat. */
object AndroidLogSink : LogSink {
    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?) {
        val prefixed = "CartoonStudio/$tag"
        when (level) {
            LogLevel.Verbose -> AndroidLog.v(prefixed, message, error)
            LogLevel.Debug -> AndroidLog.d(prefixed, message, error)
            LogLevel.Info -> AndroidLog.i(prefixed, message, error)
            LogLevel.Warn -> AndroidLog.w(prefixed, message, error)
            LogLevel.Error -> AndroidLog.e(prefixed, message, error)
        }
    }
}
