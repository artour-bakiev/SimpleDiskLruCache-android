package bakiev.artour.simpledisklrucache.library

import android.util.Log
import android.util.LruCache
import java.io.*
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashMap

class SimpleDiskLruCache(directory: File,
                         private val maxObjectsToKeep: Int,
                         maxDiskStorageSpace: Int) {

    private lateinit var workingDirectory: File
    private val diskStorageLruCache = FileLruCache(maxDiskStorageSpace)
    private val objectsLruCache = LinkedHashMap<String, Unit>(0, 0.75F, true)
    private val initializationLock = java.lang.Object()
    @Volatile
    private var initializationComplete = false
    private lateinit var logWriter: PrintWriter

    init {
        val executor = Executors.newSingleThreadExecutor()
        executor.run { init(directory) }
    }

    /**
     * Transaction pattern employed
     * try {
     *     ...
     *     writer.start().use {
     *         ...
     *         it.write(...)
     *         // OutputStream::flush should be called in order to mark transaction successful
     *         it.flush()
     *     }
     * } finally {
     *     reader = writer.done()
     * }
     */
    inner class Writer(private val directory: File, private val key: String) {
        private var file: File? = null
        private var successful = false

        fun start(): OutputStream {
            file = File(directory, UUID.randomUUID().toString())
            return ProxyOutputStream(FileOutputStream(file))
        }

        fun done(): Reader? {
            val file = this.file
            file ?: return null

            return if (successful) {
                val entry = Entry(file.absolutePath, file.length())
                val reader = Reader(entry)
                put(key, entry)
                reader
            } else {
                file.delete()
                null
            }
        }

        private inner class ProxyOutputStream internal constructor(out: OutputStream)
            : FilterOutputStream(out) {

            override fun flush() {
                successful = try {
                    super.flush()
                    true
                } catch (e: IOException) {
                    false
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

    /**
     * Transaction pattern as well
     * try {
     *     ...
     *     reader.start().use {
     *     ...
     *         it.read(...)
     *     }
     * } finally {
     *     reader.done()
     * }
     */
    class Reader internal constructor(internal val entry: Entry) {

        init {
            entry.startReading()
        }

        fun start(): InputStream = FileInputStream(entry.fileName)

        fun done() = entry.stopReading()
    }

    fun close() {
        logWriter.close()
    }

    fun write(key: String): Writer {
        waitForInitializationComplete()

        return Writer(workingDirectory, key)
    }

    fun read(key: String): Reader? {
        waitForInitializationComplete()

        val result = createReader(key)
        result ?: return null

        val file = File(result.entry.fileName)
        if (!file.exists()) {
            remove(key)
            result.done()
            return null
        }

        return result
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
    private fun createReader(key: String): Reader? {
        val entry = diskStorageLruCache.get(key)
        entry ?: return null
        return Reader(entry)
    }

    @Synchronized
    private fun put(key: String, entry: Entry) {
        val currentEntry = diskStorageLruCache.remove(key)
        currentEntry?.let {
            it.deleteFile()
            logRemoveOperation(key, it)
        }

        logPutOperation(key, entry)
        objectsLruCache[key] = Unit
        diskStorageLruCache.put(key, entry)

        while (objectsLruCache.size > maxObjectsToKeep) {
            val eldestKey = keyOfEldestInstance()
            eldestKey?.let {
                diskStorageLruCache.remove(it)
            }
        }
        if (BuildConfig.DEBUG) {
            if (objectsLruCache[key] == null) {
                // TODO: provide additional debug info like current size/configuration size
                Log.e(TAG, """
                    Improperly configured disk cache size. Item `$key` has been removed inside put operation
                """.trimIndent())
            }
        }
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
            objectsLruCache[it.key] = Unit
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

    private fun keyOfEldestInstance(): String? {
        // LinkedHashMap::eldest is hidden so lets get oldest using iterator
        val iterator = objectsLruCache.keys.iterator()
        if (!iterator.hasNext()) {
            return null
        }
        return iterator.next()
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

        override fun entryRemoved(evicted: Boolean,
                                  key: String,
                                  oldValue: Entry?,
                                  newValue: Entry?) {
            if (populating) return
            // The method is called either from SimpleDiskLruCache::put or
            // from SimpleDiskLruCache::remove so no @Synchronized annotation needed
            oldValue?.let {
                it.deleteFile()
                logRemoveOperation(key, it)
            }
            objectsLruCache.remove(key)
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
