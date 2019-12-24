package bakiev.artour.simpledisklrucache.library

import androidx.test.platform.app.InstrumentationRegistry
import org.amshove.kluent.shouldBe
import org.junit.Test
import java.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull

class DatabaseStoreTest {

    private val store: Store =
        DatabaseStore(InstrumentationRegistry.getInstrumentation().context, UUID.randomUUID().toString())

    @Test
    fun dfdf() {
        store.addEntry("www.google.com", "A", 300)

        val ddd = LruCache<String, Entry>(1000)
        store.readEntriesInto(ddd)

        ddd["wwww.google.com"].shouldNotBeNull()
        ddd["wwww.google.com"]?.fileName?.shouldBeEqualTo("A")
        ddd["wwww.google.com"]?.length?.shouldBe(300)
    }

}