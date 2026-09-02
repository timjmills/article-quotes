package com.tim.articlequotes.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Picks a new quote on the user's interval. */
class RotateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Rotator.rotate(applicationContext, notify = true, respectQuietHours = true)
        return Result.success()
    }
}

/** Pulls the latest feed once a day (only the shards that changed). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val r = FeedRepo(applicationContext, prefs).sync()
        return if (r.ok) Result.success() else Result.retry()
    }
}

object Scheduler {
    private const val ROTATE = "rotate"
    private const val SYNC = "sync"

    fun ensureScheduled(ctx: Context) = reschedule(ctx, replace = false)

    fun reschedule(ctx: Context, replace: Boolean = true) {
        val prefs = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val minutes = prefs.intervalMinutes.coerceAtLeast(1)
        if (minutes < 15) {
            // WorkManager can't repeat faster than 15 minutes; short intervals use exact alarms.
            wm.cancelUniqueWork(ROTATE)
            if (replace || !Alarms.isScheduled(ctx)) Alarms.scheduleNext(ctx, minutes)
        } else {
            Alarms.cancel(ctx)
            val rotate = PeriodicWorkRequestBuilder<RotateWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(ROTATE, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, rotate)
        }

        val net = if (prefs.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val sync = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(SYNC, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, sync)
    }

    fun rotateNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RotateWorker>().build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork("rotate-now", ExistingWorkPolicy.REPLACE, req)
    }
}

/** Exact alarms for intervals under 15 minutes. Each alarm rotates, then schedules the next one. */
object Alarms {
    private const val REQ = 42

    private fun intent(ctx: Context) = Intent(ctx, RotateAlarmReceiver::class.java).setAction(RotateAlarmReceiver.ACTION)

    private fun pending(ctx: Context, flags: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(ctx, REQ, intent(ctx), flags or PendingIntent.FLAG_IMMUTABLE)

    fun isScheduled(ctx: Context): Boolean = pending(ctx, PendingIntent.FLAG_NO_CREATE) != null

    fun scheduleNext(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = System.currentTimeMillis() + minutes * 60_000L
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        val pi = pending(ctx, PendingIntent.FLAG_NO_CREATE) ?: return
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
    }
}

class RotateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val prefs = Prefs(app)
        val minutes = prefs.intervalMinutes
        if (minutes >= 15) return  // interval was raised; WorkManager owns it now
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Rotator.rotate(app, notify = true, respectQuietHours = true) } finally {
                Alarms.scheduleNext(app, minutes)
                pending.finish()
            }
        }
    }

    companion object { const val ACTION = "com.tim.articlequotes.ROTATE_ALARM" }
}

/** Handles the "Next" and "Save" buttons on the notification. */
class QuoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_NEXT -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { Rotator.rotate(app, notify = true, respectQuietHours = false) } finally { pending.finish() }
                }
            }
            ACTION_SAVE -> {
                val prefs = Prefs(app)
                val q = prefs.currentQuote ?: return
                if (!prefs.isFavorite(q.id)) prefs.toggleFavorite(q)
                Toast.makeText(app, "Saved to your favourites", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_NEXT = "com.tim.articlequotes.NEXT"
        const val ACTION_SAVE = "com.tim.articlequotes.SAVE"
    }
}
