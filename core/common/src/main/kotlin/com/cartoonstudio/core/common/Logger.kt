package com.cartoonstudio.core.common

/**
 * Platform-agnostic logging seam.
 *
 * Core, domain and engine code never depends on `android.util.Log`; the Android
 * layer installs a real sink at startup.
 */
interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, error: Throwable?)
}

enum class LogLevel { Verbose, Debug, Info, Warn, Error }

object Log {

    @Volatile
    private var sink: LogSink = NoopSink

    @Volatile
    var minimumLevel: LogLevel = LogLevel.Debug

    fun install(sink: LogSink) {
        this.sink = sink
    }

    fun v(tag: String, message: String) = write(LogLevel.Verbose, tag, message, null)
    fun d(tag: String, message: String) = write(LogLevel.Debug, tag, message, null)
    fun i(tag: String, message: String) = write(LogLevel.Info, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(LogLevel.Warn, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(LogLevel.Error, tag, message, error)

    private fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        if (level.ordinal < minimumLevel.ordinal) return
        sink.log(level, tag, message, error)
    }

    private object NoopSink : LogSink {
        override fun log(level: LogLevel, tag: String, message: String, error: Throwable?) = Unit
    }
}
