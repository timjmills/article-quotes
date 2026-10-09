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
import com.tim.articlequotes.Notifications
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Staleness
import com.tim.articlequotes.update.Updater
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Picks a new quote on the user's interval. */
class RotateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val wait = Rotator.rotateScheduled(applicationContext)
        // SCREEN_ON: ScreenStateReceiver schedules the retry when the screen goes dark.
        if (wait > 0L) Alarms.scheduleRetry(applicationContext, wait)
        return Result.success()
    }
}

/** Pulls the latest feed once a day (only the shards that changed). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val repo = FeedRepo(applicationContext, prefs)
        val r = repo.sync()
        // Once a day: look for a newer app build, and warn if the archive has gone quiet.
        val before = prefs.availableUpdate
        val update = Updater.check(applicationContext)
        if (update != null && before != prefs.availableUpdate) Notifications.showUpdate(applicationContext, update.versionName)
        if (r.ok) Staleness.of(repo, prefs)?.let { if (it.warn && !prefs.staleNotified) { Notifications.showStale(applicationContext, it.message); prefs.staleNotified = true } }
        return if (r.ok) Result.success() else Result.retry()
    }
}

/** Sunday evening: a short review of the quotes you saved this week. */
class WeeklyReviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.weeklyReviewOn) return Result.success()
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val at = prefs.favoriteSavedAt
        val week = prefs.favorites.filter { (at[it.id] ?: 0L) >= weekAgo }
        val (title, text) = when {
            week.isNotEmpty() -> "Your week in quotes" to
                "You saved ${week.size} quote${if (week.size == 1) "" else "s"} this week. One to revisit: \u201C${week.random().text}\u201D"
            prefs.favorites.isNotEmpty() -> "From your saved quotes" to "\u201C${prefs.favorites.random().text}\u201D"
            else -> return Result.success()
        }
        Notifications.showWeekly(applicationContext, title, text)
        return Result.success()
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
        if (replace) { Alarms.cancelRetry(ctx); prefs.pendingChange = false }
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

        // Weekly review, first run next Sunday at 18:00.
        val now = LocalDateTime.now()
        var sunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(18).withMinute(0).withSecond(0)
        if (!sunday.isAfter(now)) sunday = sunday.plusWeeks(1)
        val weekly = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, sunday).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("weekly", ExistingPeriodicWorkPolicy.KEEP, weekly)
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

    fun scheduleNext(ctx: Context, minutes: Int) = scheduleIn(ctx, minutes * 60_000L)

    /** One-off retry after a postponed change (used when WorkManager owns the cadence). */
    fun scheduleRetry(ctx: Context, delayMs: Long) {
        val pi = retryPending(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        setAt(ctx, System.currentTimeMillis() + delayMs + 500L, pi)
    }

    fun scheduleIn(ctx: Context, delayMs: Long) {
        val pi = pending(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        setAt(ctx, System.currentTimeMillis() + delayMs, pi)
    }

    private fun setAt(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun retryPending(ctx: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        ctx, REQ + 1, Intent(ctx, RotateAlarmReceiver::class.java).setAction(RotateAlarmReceiver.ACTION_RETRY),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Cancels the interval chain and any pending one-off retry. */
    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pending(ctx, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        cancelRetry(ctx)
    }

    fun cancelRetry(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        retryPending(ctx, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }
}

class RotateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        when (intent.action) {
            ACTION -> {
                val minutes = prefs.intervalMinutes
                if (minutes >= 15) return  // interval was raised; WorkManager owns it now
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    var wait = 0L
                    try { wait = Rotator.rotateScheduled(app) } finally {
                        // Postponed: try again when the grace window ends, then resume the normal cadence.
                        // Screen on: keep the normal cadence; the screen-off receiver retries sooner.
                        Alarms.scheduleIn(app, if (wait > 0L) wait + 500L else minutes * 60_000L)
                        pending.finish()
                    }
                }
            }
            ACTION_RETRY -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val wait = Rotator.rotateScheduled(app)
                        if (wait > 0L) Alarms.scheduleRetry(app, wait)
                    } finally { pending.finish() }
                }
            }
        }
    }

    companion object {
        const val ACTION = "com.tim.articlequotes.ROTATE_ALARM"
        const val ACTION_RETRY = "com.tim.articlequotes.ROTATE_RETRY"
    }
}

/** Tracks when the screen turns on and off, for the grace window. Registered at runtime by App. */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                prefs.lastScreenOff = System.currentTimeMillis()
                // A change was held back while you were looking; do it once the grace has passed.
                if (prefs.pendingChange) Alarms.scheduleRetry(context.applicationContext, prefs.graceSeconds * 1000L)
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> prefs.lastScreenOn = System.currentTimeMillis()
        }
    }
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
