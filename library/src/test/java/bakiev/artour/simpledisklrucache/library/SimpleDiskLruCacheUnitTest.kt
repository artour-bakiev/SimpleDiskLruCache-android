package bakiev.artour.simpledisklrucache.library

import org.amshove.kluent.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*

class SimpleDiskLruCacheUnitTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `should return null from read if key doesn't exist`() {
        val cache = SimpleDiskLruCache(temporaryFolder.newFolder(), 10)

        cache.read("A").`should be null`()
    }

    @Test
    fun `should return non-null from write`() {
        val cache = SimpleDiskLruCache(temporaryFolder.newFolder(), 10)

        cache.write("A").`should not be null`()
    }

    @Test
    fun `should read what has been written`() {
        val cache = SimpleDiskLruCache(temporaryFolder.newFolder(), 30)
        val sampleBuffer = ByteArray(20) { n -> (n * 2).toByte() }
        cache.write("A").flush(sampleBuffer)

        cache.read("A").`should equal to`(sampleBuffer)
    }

    @Test
    fun `should respect storage space limit`() {
        val directory = temporaryFolder.newFolder()
        val cache = SimpleDiskLruCache(directory, 19)
        cache.write("A").flush(ByteArray(20))

        cache.read("A").`should be null`()
    }

    @Test
    fun `should displace oldest`() {
        val directory = temporaryFolder.newFolder()
        val cache = SimpleDiskLruCache(directory, 49)
        val sampleBufferA = ByteArray(20) { n -> (n).toByte() }
        cache.write("A").flush(sampleBufferA)
        val sampleBufferB = ByteArray(30) { n -> (n * 3).toByte() }
        cache.write("B").flush(sampleBufferB)

        cache.read("A").`should be null`()
        cache.read("B").`should equal to`(sampleBufferB)
    }

    @Test
    fun `should handle monkey multithreading test`() {
        val directory = temporaryFolder.newFolder()
        val numberOfThreads = 100
        val singleBufferSize = 20
        val cache = SimpleDiskLruCache(directory, numberOfThreads * singleBufferSize)
        val threads = mutableListOf<Thread>()
        val r = Random()
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val buffers = MutableList(numberOfThreads) { n -> ByteArray(singleBufferSize) { n.toByte() } }
        for (n in 0 until numberOfThreads) {
            val t = Thread(Runnable {
                try {
                    Thread.sleep(Math.abs(r.nextLong()) % 500)
                    cache.write(n.toString()).flush(buffers[n])
                    Thread.sleep(Math.abs(r.nextLong()) % 500)
                    cache.read(n.toString()).`should equal to`(buffers[n])
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

    private fun SimpleDiskLruCache.Writer.flush(buffer: ByteArray) {
        use {
            it.open().use { outputStream ->
                outputStream.write(buffer)
            }
        }
    }

    private fun SimpleDiskLruCache.Reader?.`should equal to`(buffer: ByteArray) {
        `should not be null`()
        use {
            it!!.open().use { inputStream ->
                val read = ByteArray(buffer.size)
                inputStream.read(read).`should equal to`(buffer.size)
                read.`should equal`(buffer)
                inputStream.read().`should equal to`(-1)
            }
        }
    }
}
