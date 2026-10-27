package dev.xeu.community.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.xeu.community.data.FeedItem
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Http
import dev.xeu.community.data.Prefs
import java.util.concurrent.TimeUnit

object RomTracker {
    private const val WORK = "rom_check"

    fun schedule(context: Context, enabled: Boolean, hours: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<RomCheckWorker>(hours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RomCheckWorker>().build())
    }

    /**
     * Fetches sources, saves the latest matching items for the ROMs screen,
     * and returns the ones not seen before. The very first run only records
     * what exists, so you aren't flooded with old builds.
     */
    suspend fun run(context: Context): List<FeedItem> {
        val prefs = Prefs(context)
        val devices = prefs.devicesNow()
        val settings = prefs.trackerNow()
        val (files, threads) = ForumRepository.romSources()

        val deviceFiles = files.filter { ForumRepository.matchesDevice(it, devices) != null }
        val relevantThreads = if (settings.notifyAllReleaseThreads) threads
            else threads.filter { ForumRepository.matchesDevice(it, devices) != null }

        val all = (deviceFiles + relevantThreads).sortedByDescending { it.epoch }
        prefs.saveLatest(all)

        val seeded = prefs.isSeeded()
        val seen = prefs.seen()
        val fresh = all.filter { it.guid !in seen }
        prefs.markSeen(files.map { it.guid } + threads.map { it.guid })
        return if (seeded) fresh else emptyList()
    }

    fun notify(context: Context, items: List<FeedItem>) {
        if (items.isEmpty()) return
        items.take(5).forEach { item ->
            val isFile = item.source == "SourceForge"
            val tap = if (isFile) PendingIntent.getActivity(
                context, item.guid.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(item.link)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ) else Notifier.appIntent(context, item.link)
            val name = item.title.substringAfterLast('/')
            Notifier.post(
                context, Notifier.CH_ROMS, item.guid.hashCode(),
                if (isFile) "New xiaomi.eu build" else "New post in ${item.source}",
                name, tap, group = "roms",
            )
        }
        if (items.size > 5) {
            Notifier.postSummary(
                context, Notifier.CH_ROMS, "roms", 1, "${items.size} new ROM updates",
                Notifier.appIntent(context, null, "roms"),
            )
        }
    }
}

class RomCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Http.init(applicationContext)
        RomTracker.notify(applicationContext, RomTracker.run(applicationContext))
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
