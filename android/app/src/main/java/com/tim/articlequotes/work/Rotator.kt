package com.tim.articlequotes.work

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import com.tim.articlequotes.Notifications
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.data.QuotePicker
import com.tim.articlequotes.ui.QuoteCardRenderer
import com.tim.articlequotes.widget.QuoteWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one place that advances to a new quote and pushes it to notification, wallpaper and widget. */
object Rotator {
    private const val TAG = "Rotator"

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
        val q = QuotePicker.pick(pool, prefs.seenIds, if (wantWallpaper) prefs.maxWallpaperChars else null)
            ?: QuotePicker.pick(pool, prefs.seenIds, null)
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
            val bmp = QuoteCardRenderer.render(q, w, h, prefs.cardStyle, prefs.textScale, showContext = prefs.showContext)
            var flags = WallpaperManager.FLAG_LOCK
            if (mode == "both") flags = flags or WallpaperManager.FLAG_SYSTEM
            wm.setBitmap(bmp, null, true, flags)
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper failed: ${e.message}")
        }
    }
}
