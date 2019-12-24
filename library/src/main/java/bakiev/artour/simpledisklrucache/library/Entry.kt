package bakiev.artour.simpledisklrucache.library

import java.io.File

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
