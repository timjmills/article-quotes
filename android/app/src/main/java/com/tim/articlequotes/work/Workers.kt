package com.tim.articlequotes.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val minutes = prefs.intervalMinutes.coerceAtLeast(15).toLong()
        val rotate = PeriodicWorkRequestBuilder<RotateWorker>(minutes, TimeUnit.MINUTES)
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(ROTATE, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, rotate)

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
