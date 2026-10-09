package com.tim.articlequotes.work

import android.app.WallpaperManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.tim.articlequotes.Notifications
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.data.QuotePicker
import com.tim.articlequotes.data.Taste
import com.tim.articlequotes.ui.QuoteCardRenderer
import com.tim.articlequotes.widget.QuoteWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one place that advances to a new quote and pushes it to notification, wallpaper and widget. */
object Rotator {
    private const val TAG = "Rotator"

    /** [graceDelayMs] result meaning "the screen is on; change once it goes dark". */
    const val SCREEN_ON = -1L

    /**
     * How long a scheduled change should wait so the quote doesn't swap under you:
     * never while the screen is on ([SCREEN_ON]), and not until [Prefs.graceSeconds] after it goes dark.
     * Returns 0 when it's fine to change now.
     */
    fun graceDelayMs(ctx: Context, prefs: Prefs, now: Long = System.currentTimeMillis()): Long {
        val grace = prefs.graceSeconds * 1000L
        if (grace <= 0L) return 0L
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return SCREEN_ON
        var off = prefs.lastScreenOff
        // Screen events are only heard while the app is alive. If the last recorded screen-off
        // predates this process, or a screen-on came after it, we can't know when the screen
        // went dark, so assume it was just now and wait the full grace.
        if (off == 0L || off < prefs.processStart || prefs.lastScreenOn > off) {
            off = now; prefs.lastScreenOff = now
        }
        return (off + grace - now).coerceAtLeast(0L)
    }

    /**
     * A timed change. Returns 0 if the quote changed (or there was nothing to do), a delay in ms
     * to retry after, or [SCREEN_ON] if it was deferred until the screen turns off.
     */
    suspend fun rotateScheduled(ctx: Context): Long = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        if (prefs.inQuietHours()) { prefs.pendingChange = false; return@withContext 0L }
        val wait = graceDelayMs(app, prefs)
        if (wait == SCREEN_ON) { prefs.pendingChange = true; return@withContext SCREEN_ON }
        if (wait > 0L) return@withContext wait
        prefs.pendingChange = false
        rotate(app, notify = true, respectQuietHours = true)
        0L
    }

    suspend fun rotate(ctx: Context, notify: Boolean, respectQuietHours: Boolean): Quote? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        if (respectQuietHours && prefs.inQuietHours()) return@withContext null
        val repo = FeedRepo(app, prefs)
        var pool = repo.quotesFor(prefs.categories)
        if (pool.isEmpty()) {
            repo.sync()
            pool = repo.quotesFor(prefs.categories)
        }
        if (pool.isEmpty()) return@withContext null
        val wantWallpaper = prefs.wallpaperMode != "off"
        val taste = Taste.from(prefs)
        // The lower position has less room, so it only takes shorter quotes.
        val maxChars = if (prefs.lockPosition == "lower") minOf(prefs.maxWallpaperChars, 170) else prefs.maxWallpaperChars
        val q = QuotePicker.pick(pool, taste, if (wantWallpaper) maxChars else null)
            ?: QuotePicker.pick(pool, taste, null)
            ?: return@withContext null
        show(app, q, notify = notify && prefs.notificationsOn)
        q
    }

    /** Make [q] the current quote (used by "Set as lock screen" on a chosen quote too). */
    suspend fun show(ctx: Context, q: Quote, notify: Boolean) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        prefs.currentQuote = q
        prefs.currentSince = System.currentTimeMillis()
        prefs.markSeen(q.id)
        prefs.pushHistory(q)
        if (notify) Notifications.show(app, q)
        applyWallpaper(app, prefs, q)
        QuoteWidget.refresh(app)
    }

    /**
     * Move to a quote already in the history (swiping back or forward). Updates the app
     * and widget at once; the caller applies the wallpaper after a short pause so a fast
     * swipe through several quotes doesn't re-render the lock screen each time.
     */
    fun showFromHistory(ctx: Context, index: Int): Quote? {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val q = prefs.history.getOrNull(index) ?: return null
        prefs.historyIndex = index
        prefs.currentQuote = q
        QuoteWidget.refresh(app)
        return q
    }

    fun applyWallpaper(ctx: Context, prefs: Prefs, q: Quote) {
        val mode = prefs.wallpaperMode
        if (mode == "off") return
        try {
            val wm = WallpaperManager.getInstance(ctx)
            val dm = ctx.resources.displayMetrics
            val w = maxOf(dm.widthPixels, 720)
            val h = maxOf(dm.heightPixels, 1280)
            val bmp = QuoteCardRenderer.render(q, w, h, prefs.cardStyle, prefs.textScale, showContext = prefs.showContext, position = prefs.lockPosition)
            var flags = WallpaperManager.FLAG_LOCK
            if (mode == "both") flags = flags or WallpaperManager.FLAG_SYSTEM
            wm.setBitmap(bmp, null, true, flags)
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper failed: ${e.message}")
        }
    }
}
