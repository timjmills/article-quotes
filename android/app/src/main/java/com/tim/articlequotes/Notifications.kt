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
    }

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
