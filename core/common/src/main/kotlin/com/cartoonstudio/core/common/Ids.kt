package com.cartoonstudio.core.common

import kotlin.random.Random

/**
 * Stable identifier generation.
 *
 * Every persistent entity in Cartoon Studio is addressed by a stable string id
 * rather than by index or by device path, so that projects stay valid when
 * content is reordered, moved between devices, or re-imported.
 */
object Ids {

    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** Monotonic counter guaranteeing uniqueness inside a single process. */
    private var counter: Long = 0L

    /**
     * Creates a new prefixed id, e.g. `node_lq3m1f0z_17`.
     *
     * The generated value is URL and filename safe.
     */
    @Synchronized
    fun next(prefix: String): String {
        counter += 1
        val stamp = System.currentTimeMillis().toString(36)
        val noise = buildString {
            repeat(4) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
        }
        return "${prefix}_${stamp}${noise}_$counter"
    }

    /** Normalizes arbitrary user text into a safe slug usable as a folder name. */
    fun slug(raw: String, fallback: String = "untitled"): String {
        val cleaned = raw.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .split('-')
            .filter { it.isNotBlank() }
            .joinToString("-")
        return cleaned.ifBlank { fallback }.take(64)
    }
}
