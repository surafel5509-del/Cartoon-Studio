package com.cartoonstudio.data.cache

/**
 * Bounded LRU cache with an explicit memory budget.
 *
 * Every cache in Cartoon Studio is bounded: a render or thumbnail cache must
 * never be the reason the app is killed, and per the format rules no cache is
 * ever required to open a project.
 */
class BoundedCache<K : Any, V : Any>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long,
    private val onEvicted: (K, V) -> Unit = { _, _ -> },
) {

    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var currentBytes = 0L

    val sizeBytes: Long get() = synchronized(entries) { currentBytes }
    val count: Int get() = synchronized(entries) { entries.size }
    val fillFraction: Float get() = if (maxBytes <= 0) 0f else (sizeBytes.toFloat() / maxBytes)

    fun get(key: K): V? = synchronized(entries) { entries[key] }

    fun put(key: K, value: V): V? = synchronized(entries) {
        val previous = entries.put(key, value)
        if (previous != null) currentBytes -= sizeOf(previous)
        currentBytes += sizeOf(value)
        trimLocked()
        previous
    }

    fun getOrPut(key: K, factory: () -> V): V = synchronized(entries) {
        entries[key] ?: factory().also { put(key, it) }
    }

    fun remove(key: K): V? = synchronized(entries) {
        entries.remove(key)?.also {
            currentBytes -= sizeOf(it)
            onEvicted(key, it)
        }
    }

    fun clear() = synchronized(entries) {
        entries.forEach { (key, value) -> onEvicted(key, value) }
        entries.clear()
        currentBytes = 0
    }

    /** Drops the least recently used half; called on memory pressure. */
    fun trimToHalf() = synchronized(entries) {
        val target = currentBytes / 2
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && currentBytes > target) {
            val entry = iterator.next()
            currentBytes -= sizeOf(entry.value)
            iterator.remove()
            onEvicted(entry.key, entry.value)
        }
    }

    private fun trimLocked() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && currentBytes > maxBytes) {
            val entry = iterator.next()
            currentBytes -= sizeOf(entry.value)
            iterator.remove()
            onEvicted(entry.key, entry.value)
        }
    }
}

/** Keys used by the frame and thumbnail caches. */
data class FrameCacheKey(
    val sceneId: String,
    val frameIndex: Int,
    val revision: Long,
    val widthPixels: Int,
)
