package bakiev.artour.simpledisklrucache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import bakiev.artour.simpledisklrucache.library.SimpleDiskLruCache
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var model: MainViewModel
    private lateinit var diskCache: SimpleDiskLruCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diskCache = SampleApplication.appComponent(this).diskCache()
        setContentView(R.layout.main_activity)
        model = ViewModelProviders.of(this)[MainViewModel::class.java]
        loadNext.setOnClickListener {
            loadImage()
        }
    }

    private fun loadImage() = model.viewModelScope.launch {
        try {
            val url = nextUrl()
            progress.visibility = View.VISIBLE
            imageUrl.text = url

            val bitmap = lookupInCache(url) ?: load(url)

            image.setImageBitmap(bitmap)
            progress.visibility = View.GONE
        } catch (throwable: Throwable) {
            progress.visibility = View.GONE
            showError(throwable)
        }
    }

    private suspend fun lookupInCache(url: String) = withContext(Dispatchers.IO) {
        val reader = diskCache.read(url)
        reader?.use {
            it.open().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }

    private suspend fun load(url: String) = withContext(Dispatchers.IO) {
        val stream: InputStream = URL(url).openStream()
        val bitmap = BitmapFactory.decodeStream(stream)
        diskCache.write(url).use {
            it.open().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        bitmap
    }

    private fun showError(throwable: Throwable) = Toast.makeText(this, throwable.message, Toast.LENGTH_SHORT).show()

    companion object {
        private val random = Random()

        private const val numberOfUniquePictures = 10

        private fun nextUrl() = "https://picsum.photos/300/300/?random/${random.nextInt(numberOfUniquePictures)}"
    }
}

class MainViewModel : ViewModel()