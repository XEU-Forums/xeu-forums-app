package dev.xeu.forums.work

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
import dev.xeu.forums.MainActivity
import dev.xeu.forums.R

object Notifier {
    const val CH_ROMS = "rom_releases"
    const val CH_ALERTS = "forum_alerts"
    const val CH_CONVERSATIONS = "conversations"
    const val CH_UPDATES = "app_updates"

    /** Intent extra that tells MainActivity which in-app screen to open. */
    const val EXTRA_OPEN = "open"
    const val OPEN_UPDATES = "updates"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ALERTS, "Forum alerts", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Replies, quotes, mentions and reactions on Xiaomi.EU" },
                NotificationChannel(CH_CONVERSATIONS, "Direct messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "New messages in your Xiaomi.EU conversations" },
                NotificationChannel(CH_ROMS, "ROM releases", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "New Xiaomi.EU builds for your devices and new release threads" },
                NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "A new version of XEU Forums is available" },
            )
        )
    }

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens [link] inside the app (forum links) when tapped. */
    fun appIntent(context: Context, link: String?, open: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        link?.let { i.data = Uri.parse(it) }
        open?.let { i.putExtra(EXTRA_OPEN, it) }
        return PendingIntent.getActivity(
            context, (link ?: open ?: "").hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @android.annotation.SuppressLint("MissingPermission") // checked by allowed()
    fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        tap: PendingIntent,
        group: String? = null,
        largeText: String? = null,
    ) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A13.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(largeText ?: text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .apply { group?.let { setGroup(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun postSummary(context: Context, channel: String, group: String, id: Int, title: String, tap: PendingIntent) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A13.toInt())
            .setContentTitle(title)
            .setContentIntent(tap)
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
