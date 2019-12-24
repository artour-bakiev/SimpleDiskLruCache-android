package bakiev.artour.simpledisklrucache.library

import android.content.Context
import java.io.*
import java.util.*
import java.util.concurrent.Executors

class SimpleDiskLruCache(context: Context, uuid: UUID, maxDiskStorageSpaceInBytes: Int) {

    private val cache: DiskLruCache

    init {
        val name = uuid.toString()
        cache = DiskLruCache(
            File(context.cacheDir, name),
            DatabaseStore(context, name),
            maxDiskStorageSpaceInBytes
        )
    }

    /**
     * reader.use {
     *     ...
     *     it.open().use { inputStream ->
     *         ...
     *         inputStream.read(...)
     *     }
     * }
     */
    fun read(key: String): Reader? = cache.read(key)

    /**
     * Transaction pattern - OutputStream::flush commits transaction
     * writer.use {
     *     ...
     *     it.open().use { outputStream ->
     *         ...
     *         outputStream.write(...)
     *         // OutputStream::flush is mandatory to complete write transaction
     *         // But it's called automatically when the stream is closed
     *         // it.flush()
     *     }
     * }
     */
    fun write(key: String): Writer = cache.write(key)

    fun close() = cache.close()

}
