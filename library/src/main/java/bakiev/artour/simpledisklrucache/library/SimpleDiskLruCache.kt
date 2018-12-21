package bakiev.artour.simpledisklrucache.library

import java.io.*
import java.util.*
import java.util.concurrent.Executors

class SimpleDiskLruCache(directory: File, maxDiskStorageSpaceInBytes: Int) {

    private lateinit var workingDirectory: File
    private val diskStorageLruCache = FileLruCache(maxDiskStorageSpaceInBytes)
    private val initializationLock = java.lang.Object()
    @Volatile
    private var initializationComplete = false
    private lateinit var logWriter: PrintWriter

    init {
        Executors.newSingleThreadExecutor().run { init(directory) }
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
            remove(key)
            return null
        }

        return Reader(entry)
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

        return Writer(workingDirectory, key)
    }

    fun close() {
        logWriter.close()
    }

    inner class Writer(private val directory: File, private val key: String) : Closeable {

        private var file: File? = null
        private var successful = false

        fun open(): OutputStream {
            val file = File(directory, UUID.randomUUID().toString())
            this.file = file
            return ProxyOutputStream(FileOutputStream(file), file)
        }

        override fun close() {
            val file = this.file
            file ?: return
            if (!successful) file.delete()
        }

        private inner class ProxyOutputStream internal constructor(out: OutputStream, private val file: File)
            : FilterOutputStream(out) {

            override fun flush() {
                successful = try {
                    super.flush()
                    put(key, Entry(file.absolutePath, file.length()))
                    true
                } catch (e: IOException) {
                    false
                }
            }

            override fun write(b: ByteArray?) {
                try {
                    super.write(b)
                } catch (e: IOException) {
                    successful = false
                }
            }

            override fun write(oneByte: Int) {
                try {
                    super.write(oneByte)
                } catch (e: IOException) {
                    successful = false
                }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                try {
                    super.write(buffer, offset, length)
                } catch (e: IOException) {
                    successful = false
                }
            }

            override fun close() {
                try {
                    super.close()
                } catch (e: IOException) {
                    successful = false
                }
            }
        }
    }

    class Reader internal constructor(internal val entry: Entry) : Closeable {

        init {
            entry.startReading()
        }

        fun open(): InputStream = FileInputStream(entry.fileName)

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
    private fun put(key: String, entry: Entry) {
        val currentEntry = diskStorageLruCache.remove(key)
        currentEntry?.let {
            it.deleteFile()
            logRemoveOperation(key, it)
        }

        logPutOperation(key, entry)
        diskStorageLruCache.put(key, entry)
    }

    @Synchronized
    private fun remove(key: String) = diskStorageLruCache.remove(key)

    private fun init(directory: File) {
        workingDirectory = File(directory, v1)
        if (!workingDirectory.exists()) {
            workingDirectory.mkdir()
        }
        // Simple and fast way to keep list of files from eldest to newest
        // TODO: But it makes sense to think about database usage instead
        val logFile = File(workingDirectory, logFile)
        if (logFile.exists()) {
            diskStorageLruCache.populating = true
            readLog(logFile)
            diskStorageLruCache.populating = false
            rebuildLog(logFile)
        } else {
            logWriter = PrintWriter(BufferedWriter(FileWriter(logFile)))
        }
        synchronized(initializationLock) {
            initializationComplete = true
            initializationLock.notifyAll()
        }
    }

    private fun rebuildLog(logFile: File) {
        logFile.delete()
        logWriter = PrintWriter(BufferedWriter(FileWriter(logFile)))
        // TODO: it's the right place to remove everything from disk which is not in diskStorageLruCache
        diskStorageLruCache.snapshot().forEach {
            logPutOperation(it.key, it.value, false)
        }
        logWriter.flush()
    }

    private fun logPutOperation(key: String, entry: Entry?, flush: Boolean = true) {
        entry ?: return
        logWriter.println(PUT + DELIMITER + key + DELIMITER + entry.fileName + DELIMITER + entry.length.toString())
        if (flush) logWriter.flush()
    }

    private fun logRemoveOperation(key: String, entry: Entry?) {
        entry ?: return
        logWriter.println(REMOVE + DELIMITER + key)
    }

    private fun readLog(logFile: File) {
        val reader = BufferedReader(FileReader(logFile))
        reader.use {
            var line = it.readLine()
            while (line != null) {
                readLogLine(line)
                line = it.readLine()
            }
        }
    }

    private fun readLogLine(line: String) {
        val tokenizer = StringTokenizer(line, DELIMITER)

        if (!tokenizer.hasMoreElements()) return

        val operation = tokenizer.nextToken()

        if (!tokenizer.hasMoreTokens()) return

        val key = tokenizer.nextToken()
        if (operation == PUT) {
            if (!tokenizer.hasMoreTokens()) return
            val fileName = tokenizer.nextToken()
            if (!tokenizer.hasMoreTokens()) return
            val length = tokenizer.nextToken().toLong()
            diskStorageLruCache.put(key, Entry(fileName, length))
        } else if (operation == REMOVE) {
            diskStorageLruCache.remove(key)
        }
    }

    internal data class Entry(val fileName: String, val length: Long) {
        private var readers = 0
        private var deleteFile = false

        @Synchronized
        fun startReading() {
            ++readers
        }

        @Synchronized
        fun stopReading() {
            if (--readers == 0 && deleteFile) {
                File(fileName).delete()
            }
        }

        @Synchronized
        fun deleteFile() {
            if (readers > 0) {
                deleteFile = true
            } else {
                File(fileName).delete()
            }
        }
    }

    private inner class FileLruCache(maxDiskStorageSpace: Int)
        : LruCache<String, Entry>(maxDiskStorageSpace) {

        var populating = false

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Entry, newValue: Entry?) {
            if (populating) return
            // The method is called either from SimpleDiskLruCache::put or
            // from SimpleDiskLruCache::remove so no synchronization is required
            oldValue.let {
                it.deleteFile()
                logRemoveOperation(key, it)
            }
        }

        override fun sizeOf(key: String, value: Entry): Int = value.length.toInt()
    }

    companion object {
        private const val TAG = "SimpleDiskLruCache"
        private const val v1 = "DCE584D6-7786-4332-93C4-EE7B33588F4C"
        private const val logFile = "logFile"
        private const val PUT = "PUT"
        private const val REMOVE = "REMOVE"
        private const val DELIMITER = "|"
    }
}
