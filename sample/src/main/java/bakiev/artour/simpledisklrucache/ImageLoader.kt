package bakiev.artour.simpledisklrucache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.Subject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URL

object ImageLoader {
    fun load(url: String): Observable<Bitmap> = Subject
            .fromCallable { loadBitmap(url) }
            .subscribeOn(Schedulers.computation())

    private fun loadBitmap(url: String): Bitmap {
        val inputStream: InputStream = URL(url).openStream()

        return BufferedInputStream(inputStream).use {
            BitmapFactory.decodeStream(it)
                    ?: throw IllegalArgumentException("Failed to obtain image from $url")
        }
    }
}
