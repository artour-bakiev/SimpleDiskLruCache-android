package bakiev.artour.simpledisklrucache.library

import org.amshove.kluent.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.*
import kotlin.math.abs

class DiskLruCacheUnitTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val store = createStore()
    private lateinit var directory: File

    @Before
    fun setup() {
        directory = temporaryFolder.newFolder()
    }

    @Test
    fun `should return null from read if key doesn't exist`() {
        val cache = createDiskLruCache(store, directory, 10)

        cache.read("A").`should be null`()
        store.read().size() `should be equal to` 0
    }

    @Test
    fun `should read what has been written`() {
        val cache = createDiskLruCache(store, directory, 30)
        val sampleBuffer = ByteArray(20) { n -> (n * 2).toByte() }
        cache.write("A").flush(sampleBuffer)

        cache.read("A").`should be equal to`(sampleBuffer)
        val lru = store.read()
        lru.size() `should be equal to` 1
        lru["A"].`should not be null`()
    }

    @Test
    fun `should respect storage space limit`() {
        val cache = createDiskLruCache(store, directory, 19)
        cache.write("A").flush(ByteArray(20))

        cache.read("A").`should be null`()
        store.read().size() `should be equal to` 0
    }

    @Test
    fun `should displace oldest`() {
        val cache = createDiskLruCache(store, directory, 49)
        cache.write("A").flush(ByteArray(20))
        val sampleBufferForKeyB = ByteArray(30) { n -> (n * 3).toByte() }
        cache.write("B").flush(sampleBufferForKeyB)

        cache.read("A").`should be null`()
        cache.read("B").`should be equal to`(sampleBufferForKeyB)
        val lru = store.read()
        lru.size() `should be equal to` 1
        lru["A"].`should be null`()
        lru["B"].`should not be null`()
    }

    @Test
    fun `should properly handle storage space reducing`() {
        val directory = directory
        val store = createStore()
        val cache1 = createDiskLruCache(store, directory, 50)
        val sampleBufferA = ByteArray(45) { n -> (n).toByte() }
        val keyA = "http://google.com?param=34&value=30"
        cache1.write(keyA).flush(sampleBufferA)
        val sampleBufferB = ByteArray(5) { n -> (n * 3).toByte() }
        val keyB = "http://google.com?param=34&value=31"
        cache1.write(keyB).flush(sampleBufferB)
        cache1.read(keyA).`should not be null`()

        cache1.close()
        val cache2 = createDiskLruCache(store, directory, 49)
        val keyAFound = cache2.read(keyA) != null
        val keyBFound = cache2.read(keyB) != null
        keyAFound `should not be equal to` keyBFound
        cache2.read(keyA)?.`should be equal to`(sampleBufferA)
        cache2.read(keyB)?.`should be equal to`(sampleBufferB)
        cache2.read(keyA) `should not equal` cache2.read(keyB)
        val lru = store.read()
        lru.size() `should be equal to` 1
        val lruAFound = lru[keyA] != null
        val lruBFound = lru[keyB] != null
        lruAFound `should be equal to` keyAFound
        lruBFound `should be equal to` keyBFound
    }

    @Test
    fun `should handle monkey multithreading test`() {
        val numberOfThreads = 100
        val singleBufferSize = 20
        val cache = createDiskLruCache(store, directory, numberOfThreads * singleBufferSize)
        val threads = mutableListOf<Thread>()
        val r = Random()
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val buffers =
            MutableList(numberOfThreads) { n -> ByteArray(singleBufferSize) { n.toByte() } }
        for (n in 0 until numberOfThreads) {
            val t = Thread(Runnable {
                try {
                    Thread.sleep(abs(r.nextLong()) % 500)
                    cache.write(n.toString()).flush(buffers[n])
                    Thread.sleep(abs(r.nextLong()) % 500)
                    cache.read(n.toString()).`should be equal to`(buffers[n])
                } catch (t: Throwable) {
                    errors.add(t.message)
                }
            })
            threads.add(t)
            t.start()
        }
        for (t in threads) {
            t.join()
        }

        errors.`should be empty`()
    }

    private fun createDiskLruCache(store: Store, directory: File, maxDiskStorageSpaceInBytes: Int): DiskLruCache =
        DiskLruCache(directory, store, maxDiskStorageSpaceInBytes)

    private fun createStore(): Store = object : Store {
        private val map: MutableMap<String, Entry> = mutableMapOf()

        override fun readEntriesInto(lruCache: LruCache<String, Entry>) {
            map.forEach { (key, entry) -> lruCache.put(key, entry) }
        }

        override fun removeEntry(key: String) {
            map.remove(key)
        }

        override fun addEntry(key: String, fileName: String, length: Long) {
            map[key] = Entry(fileName, length)
        }

        override fun close() = Unit

    }

    private fun Writer.flush(buffer: ByteArray) = use {
        it.open().use { outputStream ->
            outputStream.write(buffer)
        }
    }

    private fun Reader?.`should be equal to`(buffer: ByteArray) {
        `should not be null`()
        use {
            it?.open().use { inputStream ->
                val read = ByteArray(buffer.size)
                inputStream?.read(read)?.`should be equal to`(buffer.size)
                read.`should equal`(buffer)
                inputStream?.read()?.`should be equal to`(-1)
            }
        }
    }

    private fun Store.read(): LruCache<String, Entry> {
        val lruCache = LruCache<String, Entry>(2_000_000_000)
        readEntriesInto(lruCache)
        return lruCache
    }
}
