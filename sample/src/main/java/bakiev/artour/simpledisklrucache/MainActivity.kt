package bakiev.artour.simpledisklrucache

import android.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import bakiev.artour.simpledisklrucache.databinding.MainActivityBinding
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.presenter = Presenter()
    }

    inner class Presenter {

        fun loadNext() {
            ImageLoader
                    .load(nextUrl())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ bitmap -> update(bitmap) }) { throwable -> showError(throwable) }
        }

        private fun showError(throwable: Throwable) = Toast.makeText(this@MainActivity, throwable.message, Toast.LENGTH_SHORT).show()

        private fun update(bitmap: Bitmap) = binding.image.setImageBitmap(bitmap)
    }

    companion object {
        private val random = Random()

        private const val numberOfUniquePictures = 100

        private fun nextUrl() = "https://picsum.photos/300/300/?random/${random.nextInt(numberOfUniquePictures)}"
    }
}
