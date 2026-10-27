package dev.xeu.community.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.xeu.community.MainActivity
import dev.xeu.community.R
import dev.xeu.community.data.FeedItem
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Http
import dev.xeu.community.data.Prefs
import java.util.concurrent.TimeUnit

const val CHANNEL_ROMS = "rom_releases"

object RomTracker {
    private const val WORK = "rom_check"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL_ROMS, "ROM releases", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "New xiaomi.eu builds for your devices and new release threads"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

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

    @android.annotation.SuppressLint("MissingPermission") // checked at the top
    fun notify(context: Context, items: List<FeedItem>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        items.take(5).forEach { item ->
            val isFile = item.source == "SourceForge"
            val intent = if (isFile) Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                else Intent(context, MainActivity::class.java).setData(Uri.parse(item.link))
            val pi = PendingIntent.getActivity(context, item.guid.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL_ROMS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (isFile) "New xiaomi.eu build" else "New post in ${item.source}")
                .setContentText(item.title.substringAfterLast('/'))
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.title.substringAfterLast('/')))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup("roms")
                .build()
            runCatching { nm.notify(item.guid.hashCode(), n) }
        }
        if (items.size > 5) {
            val n = NotificationCompat.Builder(context, CHANNEL_ROMS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${items.size} new ROM updates")
                .setContentText("Open the ROMs tab to see them all")
                .setContentIntent(PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .setGroup("roms").setGroupSummary(true).setAutoCancel(true)
                .build()
            runCatching { nm.notify(1, n) }
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
