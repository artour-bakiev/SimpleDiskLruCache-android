package bakiev.artour.simpledisklrucache.library

import android.content.Context
//import com.google.common.truth.Truth.assertThat
//import com.google.common.truth.ExpectFailure.assertThat
//import com.nhaarman.mockitokotlin2.doReturn
//import com.nhaarman.mockitokotlin2.mock
//import org.amshove.kluent.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
//import org.mockito.Mockito
import java.io.File
import java.util.*
import kotlin.math.abs

class SimpleDiskLruCacheUnitTest2 {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

//    @Test
//    fun shouldReturnNullFromFeadIfKeyDoesNotExist() {
//        val cache = createSimpleDiskLruCache(temporaryFolder.newFolder(), 10)
//
//        assertThat(cache.read("A")).isNull()
//    }

//    @Test
//    fun `should return non-null from write`() {
//        val cache = createSimpleDiskLruCache(temporaryFolder.newFolder(), 10)
//
//        cache.write("A").`should not be null`()
//    }
//
//    @Test
//    fun `should read what has been written`() {
//        val cache = createSimpleDiskLruCache(temporaryFolder.newFolder(), 30)
//        val sampleBuffer = ByteArray(20) { n -> (n * 2).toByte() }
//        cache.write("A").flush(sampleBuffer)
//
//        cache.read("A").`should equal to`(sampleBuffer)
//    }
//
//    @Test
//    fun `should respect storage space limit`() {
//        val directory = temporaryFolder.newFolder()
//        val cache = createSimpleDiskLruCache(directory, 19)
//        cache.write("A").flush(ByteArray(20))
//
//        cache.read("A").`should be null`()
//    }
//
//    @Test
//    fun `should displace oldest`() {
//        val directory = temporaryFolder.newFolder()
//        val cache = createSimpleDiskLruCache(directory, 49)
//        val sampleBufferA = ByteArray(20) { n -> (n).toByte() }
//        cache.write("A").flush(sampleBufferA)
//        val sampleBufferB = ByteArray(30) { n -> (n * 3).toByte() }
//        cache.write("B").flush(sampleBufferB)
//
//        cache.read("A").`should be null`()
//        cache.read("B").`should equal to`(sampleBufferB)
//    }
//
//    @Test
//    fun `should handle monkey multithreading test`() {
//        val directory = temporaryFolder.newFolder()
//        val numberOfThreads = 100
//        val singleBufferSize = 20
//        val cache = createSimpleDiskLruCache(directory, numberOfThreads * singleBufferSize)
//        val threads = mutableListOf<Thread>()
//        val r = Random()
//        val errors = Collections.synchronizedList(mutableListOf<String>())
//        val buffers =
//            MutableList(numberOfThreads) { n -> ByteArray(singleBufferSize) { n.toByte() } }
//        for (n in 0 until numberOfThreads) {
//            val t = Thread(Runnable {
//                try {
//                    Thread.sleep(abs(r.nextLong()) % 500)
//                    cache.write(n.toString()).flush(buffers[n])
//                    Thread.sleep(abs(r.nextLong()) % 500)
//                    cache.read(n.toString()).`should equal to`(buffers[n])
//                } catch (t: Throwable) {
//                    errors.add(t.message)
//                }
//            })
//            threads.add(t)
//            t.start()
//        }
//        for (t in threads) {
//            t.join()
//        }
//
//        errors.`should be empty`()
//    }

//    private fun createSimpleDiskLruCache(
//        directory: File,
//        maxDiskStorageSpaceInBytes: Int
//    ): SimpleDiskLruCache {
////        val context = mock<Context> {
////            on { cacheDir } doReturn directory
////        }
//
//
//        val context = Mockito.mock(Context::class.java)
//        Mockito.`when`(context.cacheDir).thenReturn(directory)
//        return SimpleDiskLruCache(context, UUID.randomUUID(), maxDiskStorageSpaceInBytes)
////        val context: Context = mock {
////            on { cacheDir } doReturn
////        }
//    }

    private fun Writer.flush(buffer: ByteArray) {
        use {
            it.open().use { outputStream ->
                outputStream.write(buffer)
            }
        }
    }

//    private fun SimpleDiskLruCache.Reader?.`should equal to`(buffer: ByteArray) {
//        `should not be null`()
//        use {
//            it!!.open().use { inputStream ->
//                val read = ByteArray(buffer.size)
//                inputStream.read(read).`should be equal to`(buffer.size)
//                read.`should equal`(buffer)
//                inputStream.read().`should be equal to`(-1)
//            }
//        }
//    }
}
