package bakiev.artour.simpledisklrucache.library

import java.io.*
import java.util.*
import java.util.concurrent.Executors

internal open class DiskLruCache(
    private val workingDirectory: File,
    private val store: Store,
    maxDiskStorageSpaceInBytes: Int
) {

    private val diskStorageLruCache = FileLruCache(maxDiskStorageSpaceInBytes)
    private val initializationLock = Object()
    @Volatile
    private var initializationComplete = false

    init {
        Executors.newSingleThreadExecutor().run { init() }
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
    fun read(key: String): Reader? {
        waitForInitializationComplete()

        val entry = findEntry(key)
        entry ?: return null

        val file = File(entry.fileName)
        if (!file.exists()) {
            removeEntry(key)
            return null
        }

        return ReaderInternal(entry)
    }

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
    fun write(key: String): Writer {
        waitForInitializationComplete()

        return WriterInternal(workingDirectory, key)
    }

    fun close() = store.close()

    inner class WriterInternal(private val directory: File, private val key: String) : Writer {

        private var file: File? = null
        private var successful = false

        override fun open(): OutputStream {
            val file = File(directory, UUID.randomUUID().toString())
            this.file = file
            return ProxyOutputStream(FileOutputStream(file), file)
        }

        override fun close() {
            val file = this.file
            file ?: return
            if (!successful) file.delete()
        }

        private inner class ProxyOutputStream internal constructor(
            out: OutputStream,
            private val file: File
        ) : FilterOutputStream(out) {

            override fun flush() {
                try {
                    super.flush()
                    addEntry(key, Entry(file.absolutePath, file.length()))
                    successful = true
                } catch (e: IOException) {
                    successful = false
                    throw e
                }
            }

            override fun write(b: ByteArray) {
                try {
                    super.write(b)
                } catch (e: IOException) {
                    successful = false
                    throw e
                }
            }

            override fun write(oneByte: Int) {
                try {
                    super.write(oneByte)
                } catch (e: IOException) {
                    successful = false
                    throw e
                }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                try {
                    super.write(buffer, offset, length)
                } catch (e: IOException) {
                    successful = false
                    throw e
                }
            }

            override fun close() {
                try {
                    super.close()
                } catch (e: IOException) {
                    successful = false
                    throw e
                }
            }
        }
    }

    class ReaderInternal internal constructor(private val entry: Entry) : Reader {

        init {
            entry.startReading()
        }

        override fun open(): InputStream = FileInputStream(entry.fileName)

        override fun close() = entry.stopReading()
    }

    private fun waitForInitializationComplete() {
        if (initializationComplete) {
            return
        }
        synchronized(initializationLock) {
            if (!initializationComplete) {
                initializationLock.wait()
            }
        }
    }

    @Synchronized
    private fun findEntry(key: String): Entry? = diskStorageLruCache[key]

    @Synchronized
    private fun addEntry(key: String, entry: Entry) {
        val currentEntry = diskStorageLruCache.remove(key)
        currentEntry?.let {
            it.deleteFile()
            store.removeEntry(key)
        }

        store.addEntry(key, entry.fileName, entry.length)
        diskStorageLruCache.put(key, entry)
    }

    @Synchronized
    private fun removeEntry(key: String) = diskStorageLruCache.remove(key)

    private fun init() {
        if (!workingDirectory.exists()) {
            workingDirectory.mkdir()
        }

        store.readEntriesInto(diskStorageLruCache)

        synchronized(initializationLock) {
            initializationComplete = true
            initializationLock.notifyAll()
        }
    }

    private inner class FileLruCache(maxDiskStorageSpace: Int) : LruCache<String, Entry>(maxDiskStorageSpace) {

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Entry, newValue: Entry?) {
            // The method is called either from SimpleDiskLruCache::put or
            // from SimpleDiskLruCache::remove so no synchronization is required
            oldValue.let {
                it.deleteFile()
                store.removeEntry(key)
            }
        }

        override fun sizeOf(key: String, value: Entry): Int = value.length.toInt()
    }
}
