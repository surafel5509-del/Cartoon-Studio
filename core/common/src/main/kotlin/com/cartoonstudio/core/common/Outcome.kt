package com.cartoonstudio.core.common

/**
 * A deterministic success/failure wrapper used across engine and data layers.
 *
 * The editor must never crash because a project file is damaged or an export
 * target is unavailable, so fallible operations return [Outcome] instead of
 * throwing.
 */
sealed interface Outcome<out T> {

    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error

    fun getOrElse(fallback: @UnsafeVariance T): T = getOrNull() ?: fallback

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): Outcome<T> {
        if (this is Success) action(value)
        return this
    }

    fun onFailure(action: (AppError) -> Unit): Outcome<T> {
        if (this is Failure) action(error)
        return this
    }

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)
        fun failure(error: AppError): Outcome<Nothing> = Failure(error)
    }
}

/** Structured, user-presentable error taxonomy. */
sealed class AppError(
    val message: String,
    val cause: Throwable? = null,
) {
    class NotFound(what: String) : AppError("$what was not found")
    class Invalid(reason: String) : AppError(reason)
    class Corrupt(what: String, cause: Throwable? = null) : AppError("$what is damaged", cause)
    class Io(reason: String, cause: Throwable? = null) : AppError(reason, cause)
    class Unsupported(what: String) : AppError("$what is not supported on this device")
    class Cancelled(what: String) : AppError("$what was cancelled")
    class Unknown(cause: Throwable?) : AppError(cause?.message ?: "Unexpected error", cause)

    override fun toString(): String = "${this::class.simpleName}: $message"
}

/** Runs [block], converting any thrown exception into an [Outcome.Failure]. */
inline fun <T> runCatchingOutcome(what: String, block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    if (t is kotlin.coroutines.cancellation.CancellationException) throw t
    Outcome.Failure(AppError.Io("$what failed: ${t.message ?: t::class.simpleName}", t))
}
