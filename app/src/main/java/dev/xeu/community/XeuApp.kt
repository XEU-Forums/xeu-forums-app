package dev.xeu.community

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.xeu.community.data.Http
import dev.xeu.community.work.Notifier
import dev.xeu.community.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XeuApp : Application(), ImageLoaderFactory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        Notifier.createChannels(this)
        appScope.launch { Scheduler.applyAll(this@XeuApp) }
    }

    // Images go through the same client, so attachments that need a login still load.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient { Http.client }.crossfade(true).build()
}
