package bakiev.artour.simpledisklrucache.library

import java.util.*

internal open class LruCache<K, V>(maxSize: Int) {
    private val map: LinkedHashMap<K, V>
    private var size: Int = 0
    private var maxSize: Int = 0
    private var putCount: Int = 0
    private var createCount: Int = 0
    private var evictionCount: Int = 0
    private var hitCount: Int = 0
    private var missCount: Int = 0

    init {
        require(maxSize > 0) { "maxSize <= 0" }

        this.maxSize = maxSize

        map = LinkedHashMap(0, 0.75f, true)
    }

    fun resize(maxSize: Int) {
        require(maxSize > 0) { "maxSize <= 0" }

        this.maxSize = maxSize

        trimToSize(maxSize)
    }

    operator fun get(key: K): V? {
        if (key == null) {
            throw NullPointerException("key == null")
        } else {
            var mapValue = map[key]
            if (mapValue != null) {
                ++hitCount
                return mapValue
            }

            ++missCount

            val createdValue: V? = null
            if (createdValue == null) {
                return null
            } else {
                ++createCount
                mapValue = map.put(key, createdValue)
                if (mapValue != null) {
                    map[key] = mapValue
                } else {
                    size += safeSizeOf(key, createdValue)
                }

                return if (mapValue != null) {
                    entryRemoved(false, key, createdValue, mapValue)
                    mapValue
                } else {
                    trimToSize(maxSize)
                    createdValue
                }
            }
        }
    }

    fun put(key: K, value: V): V? {
        if (key != null && value != null) {
            ++putCount
            size += safeSizeOf(key, value)
            val previous = map.put(key, value)
            if (previous != null) {
                size -= safeSizeOf(key, previous)
            }

            if (previous != null) {
                entryRemoved(false, key, previous, value)
            }

            trimToSize(maxSize)
            return previous
        } else {
            throw NullPointerException("key == null || value == null")
        }
    }

    private fun trimToSize(maxSize: Int) {
        while (true) {
            if (size < 0 || map.isEmpty() && size != 0) {
                throw IllegalStateException(javaClass.name + ".sizeOf() is reporting inconsistent results!")
            }

            if (size <= maxSize || map.isEmpty()) {
                return
            }

            val toEvict = map.entries.iterator().next()
            val key = toEvict.key
            val value = toEvict.value
            map.remove(key)
            size -= safeSizeOf(key, value)
            ++evictionCount

            entryRemoved(true, key, value, null)
        }
    }

    fun remove(key: K): V? {
        if (key == null) {
            throw NullPointerException("key == null")
        } else {
            val previous = map.remove(key)
            if (previous != null) {
                size -= safeSizeOf(key, previous)
            }

            if (previous != null) {
                entryRemoved(false, key, previous, null)
            }

            return previous
        }
    }

    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    private fun safeSizeOf(key: K, value: V): Int {
        val result = sizeOf(key, value)
        return if (result < 0) {
            throw IllegalStateException("Negative size: $key=$value")
        } else {
            result
        }
    }

    protected open fun sizeOf(key: K, value: V): Int {
        return 1
    }

    fun evictAll() {
        trimToSize(-1)
    }

    fun size(): Int {
        return size
    }

    fun maxSize(): Int {
        return maxSize
    }

    fun hitCount(): Int {
        return hitCount
    }

    fun missCount(): Int {
        return missCount
    }

    fun createCount(): Int {
        return createCount
    }

    fun putCount(): Int {
        return putCount
    }

    fun evictionCount(): Int {
        return evictionCount
    }

    fun snapshot(): Map<K, V> {
        return LinkedHashMap(map)
    }

    override fun toString(): String {
        val accesses = hitCount + missCount
        val hitPercent = if (accesses != 0) 100 * hitCount / accesses else 0
        return String.format(
            Locale.US,
            "LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
            maxSize,
            hitCount,
            missCount,
            hitPercent
        )
    }
}
