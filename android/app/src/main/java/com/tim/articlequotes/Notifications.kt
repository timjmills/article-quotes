package com.tim.articlequotes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.ui.MainActivity
import com.tim.articlequotes.work.QuoteActionReceiver

object Notifications {
    const val CHANNEL = "quotes"
    const val ID = 1001

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_quotes), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = ctx.getString(R.string.channel_quotes_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INFO, "Updates and weekly review", NotificationManager.IMPORTANCE_LOW).apply {
            description = "App updates, your weekly review of saved quotes, and feed problems."
            setShowBadge(true)
        })
    }

    const val CHANNEL_INFO = "info"
    const val ID_UPDATE = 1002
    const val ID_WEEKLY = 1003
    const val ID_STALE = 1004

    private fun openTab(ctx: Context, tab: Int, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TAB, tab)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(ctx: Context, id: Int, title: String, text: String, tab: Int) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openTab(ctx, tab, 100 + id))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    fun showUpdate(ctx: Context, versionName: String) =
        post(ctx, ID_UPDATE, "Article Quotes $versionName is ready", "Tap to update. It installs over this version and keeps your settings and saved quotes.", MainActivity.TAB_SETTINGS)

    fun showInstallReady(ctx: Context, install: Intent) {
        if (!canPost(ctx)) return
        val pi = PendingIntent.getActivity(ctx, 200, install, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update downloaded")
            .setContentText("Tap to install the new version of Article Quotes.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_UPDATE, n) } catch (_: SecurityException) { }
    }

    fun showWeekly(ctx: Context, title: String, text: String) = post(ctx, ID_WEEKLY, title, text, MainActivity.TAB_SAVED)

    fun showStale(ctx: Context, text: String) = post(ctx, ID_STALE, "No new articles lately", text, MainActivity.TAB_TODAY)

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun show(ctx: Context, q: Quote) {
        if (!canPost(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ARTICLE, q.articleId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextPi = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, QuoteActionReceiver::class.java).setAction(QuoteActionReceiver.ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val savePi = PendingIntent.getBroadcast(
            ctx, 3, Intent(ctx, QuoteActionReceiver::class.java).setAction(QuoteActionReceiver.ACTION_SAVE).putExtra("quoteId", q.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val attribution = "— ${q.author} · ${q.title}"
        val big = buildString {
            append("“").append(q.text).append("”")
            if (q.context.isNotBlank()) append("\n\nWhy it matters: ").append(q.context)
            append("\n\n").append(attribution)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(q.text)
            .setContentText(if (q.context.isNotBlank()) q.context else attribution)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big).setSummaryText(q.category))
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(0, "Read summary", openPi)
            .addAction(0, "Save", savePi)
            .addAction(0, "Next", nextPi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (_: SecurityException) {
        }
    }
}
