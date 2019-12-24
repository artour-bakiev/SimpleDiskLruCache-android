package bakiev.artour.simpledisklrucache.library

internal interface Store {

    fun readEntriesInto(lruCache: LruCache<String, Entry>)

    fun removeEntry(key: String)

    fun addEntry(key: String, fileName: String, length: Long)

    fun close();
}
