package bakiev.artour.simpledisklrucache.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

internal class DatabaseStore(context: Context, name: String) :
    Store,
    SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(EntryDDDD.CREATE_TABLE)
        db.execSQL((EntryDDDD.CREATE_INDEX))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun readEntriesInto(lruCache: LruCache<String, Entry>) {
        val cursor = readableDatabase.query(
            EntryDDDD.TABLE_NAME,
            arrayOf(
                EntryDDDD.COLUMN_NAME_KEY,
                EntryDDDD.COLUMN_NAME_NAME,
                EntryDDDD.COLUMN_NAME_LENGTH
            ),
            null,
            null,
            null,
            null,
            BaseColumns._ID
        )
        cursor ?: return

        if (!cursor.moveToFirst()) {
            return
        }

        cursor.use {
            while (!it.isAfterLast) {
                val key = it.getString(0)
                val name = it.getString(1)
                val length = it.getLong(2)
                lruCache.put(key, Entry(name, length))
                cursor.moveToNext()
            }
        }
    }

    override fun removeEntry(key: String) {
        writableDatabase.delete(
            EntryDDDD.TABLE_NAME,
            "${EntryDDDD.COLUMN_NAME_KEY}=?",
            arrayOf(key)
        )
    }

    override fun addEntry(key: String, fileName: String, length: Long) {
        val values = EntryDDDD.createValues(key, fileName, length)
        writableDatabase.insert(EntryDDDD.TABLE_NAME, null, values)
    }

    object EntryDDDD : BaseColumns {
        const val TABLE_NAME = "entry"
        const val COLUMN_NAME_KEY = "key"
        const val COLUMN_NAME_NAME = "name"
        const val COLUMN_NAME_LENGTH = "length"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME_KEY TEXT,
                $COLUMN_NAME_NAME TEXT,
                $COLUMN_NAME_LENGTH INTEGER
            )
        """
        const val CREATE_INDEX = "CREATE INDEX entry_key on $TABLE_NAME($COLUMN_NAME_KEY)"

        fun createValues(key: String, name: String, length: Long) = ContentValues(3).apply {
            put(COLUMN_NAME_KEY, key)
            put(COLUMN_NAME_NAME, name)
            put(COLUMN_NAME_LENGTH, length)
        }
    }

    companion object {
        const val VERSION = 1
    }
}
