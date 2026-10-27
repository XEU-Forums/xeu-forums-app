package dev.xeu.community.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.xeu.community.data.Accounts
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Http
import dev.xeu.community.data.Prefs
import dev.xeu.community.data.SettingsStore
import dev.xeu.community.update.Updater
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val FORUM = "forum_notifications"
    private const val UPDATE = "app_update_check"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Re-applies every background job from the current settings. Safe to call often. */
    suspend fun applyAll(context: Context) {
        val s = SettingsStore(context).now()
        val wm = WorkManager.getInstance(context)

        if (s.notifyAlerts || s.notifyConversations) {
            // Android won't run periodic work more often than every 15 minutes.
            val req = PeriodicWorkRequestBuilder<ForumNotificationWorker>(
                s.forumCheckMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES
            ).setConstraints(online).build()
            wm.enqueueUniquePeriodicWork(FORUM, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork(FORUM)

        if (s.autoCheckUpdates) {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(online).build()
            wm.enqueueUniquePeriodicWork(UPDATE, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork(UPDATE)

        val t = Prefs(context).trackerNow()
        RomTracker.schedule(context, t.enabled, t.intervalHours)
    }

    fun checkForumNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ForumNotificationWorker>().setConstraints(online).build()
        )
    }
}

/**
 * Looks at the unread badges in the forum header and, when they go up, fetches the
 * new alerts / conversations and posts a notification for each one.
 * The first run for an account only records what already exists.
 */
class ForumNotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        Http.init(context)
        val settings = SettingsStore(context)
        val s = settings.now()
        return try {
            val me = ForumRepository.me() ?: return Result.success() // not logged in
            val account = me.userId.toString()
            // Keep the saved session fresh (the forum rotates cookies).
            runCatching { Accounts(context).saveCurrent(me) }

            if (s.notifyAlerts) {
                val last = settings.lastAlertId(account)
                if (me.unreadAlerts > 0 || last < 0) {
                    val alerts = ForumRepository.alerts()
                    val maxId = alerts.maxOfOrNull { it.id } ?: last
                    if (last >= 0) {
                        val fresh = alerts.filter { it.id > last && it.unread }.sortedBy { it.id }
                        fresh.takeLast(6).forEach { a ->
                            Notifier.post(
                                context, Notifier.CH_ALERTS, ("a" + a.id).hashCode(),
                                me.username, a.text, Notifier.appIntent(context, a.link),
                                group = "alerts_$account",
                            )
                        }
                        if (fresh.size > 1) Notifier.postSummary(
                            context, Notifier.CH_ALERTS, "alerts_$account", ("as$account").hashCode(),
                            "${fresh.size} new alerts", Notifier.appIntent(context, null, "alerts"),
                        )
                    }
                    if (maxId > last) settings.setLastAlertId(account, maxId)
                }
            }

            if (s.notifyConversations) {
                val seen = settings.seenConversations(account)
                if (me.unreadConversations > 0 || seen.isEmpty()) {
                    val convos = ForumRepository.conversations()
                    val firstRun = seen.isEmpty()
                    val updated = seen.toMutableMap()
                    convos.forEach { c ->
                        val marker = "${c.replies}:${c.lastEpoch}"
                        if (!firstRun && c.unread && seen[c.id] != marker) {
                            Notifier.post(
                                context, Notifier.CH_CONVERSATIONS, ("c" + c.id).hashCode(),
                                c.lastSender.ifBlank { c.participants.firstOrNull() ?: "New message" },
                                c.title, Notifier.appIntent(context, c.url),
                            )
                        }
                        updated[c.id] = marker
                    }
                    // A placeholder keeps "first run" from repeating when the inbox is empty.
                    if (updated.isEmpty()) updated[0] = "seeded"
                    settings.setSeenConversations(account, updated)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        Http.init(context)
        val store = SettingsStore(context)
        return try {
            val info = Updater.latest(store.now().updateChannel)
            if (info.isNewer && info.versionCode != store.skippedVersion()) {
                Notifier.post(
                    context, Notifier.CH_UPDATES, 4242,
                    "Update available: ${info.versionName}",
                    "Tap to download and install the new version.",
                    Notifier.appIntent(context, null, Notifier.OPEN_UPDATES),
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.success() // try again at the next scheduled check
        }
    }
}
