package bakiev.artour.simpledisklrucache

import android.app.Application
import android.content.Context
import bakiev.artour.simpledisklrucache.library.SimpleDiskLruCache
import dagger.Component
import dagger.Module
import dagger.Provides
import java.util.*
import javax.inject.Singleton

@Singleton
@Component(modules = [SampleApplication.ApplicationModule::class])
interface ApplicationComponent {

    fun diskCache(): SimpleDiskLruCache
}

class SampleApplication : Application() {

    private val applicationComponent = DaggerApplicationComponent
        .builder()
        .applicationModule(ApplicationModule(this))
        .build()

    companion object {
        private val uuid = UUID.fromString("740B2493-235E-4B6A-B1E6-469527AB90EA")
        private const val maxDiskStorageSpaceInBytes = 1024 * 1024 * 10

        fun appComponent(context: Context): ApplicationComponent {
            val app = context.applicationContext as SampleApplication
            return app.applicationComponent
        }
    }

    @Module
    class ApplicationModule(private val context: Context) {

        @[Provides Singleton]
        fun provideDiskCache(): SimpleDiskLruCache = SimpleDiskLruCache(
            context,
            uuid,
            maxDiskStorageSpaceInBytes
        )
    }

    override fun onTerminate() {
        applicationComponent.diskCache().close()
        super.onTerminate()
    }
}