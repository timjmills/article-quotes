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
import com.tim.articlequotes.data.DevotionalStore
import com.tim.articlequotes.data.EsvClient
import com.tim.articlequotes.data.ProverbChunk
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
        // Reader open (or waiting behind a dark screen): keep its quote until it is closed.
        val seen = prefs.readerSeenAt
        if (seen > 0 && now - seen < com.tim.articlequotes.ui.ReaderActivity.KEEP_WHILE_DARK_MS) return SCREEN_ON
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
        val wantWallpaper = prefs.wallpaperMode != "off"
        val taste = Taste.from(prefs)
        // The lower position has less room, so it only takes shorter quotes.
        val maxChars = if (prefs.lockPosition == "lower") minOf(prefs.maxWallpaperChars, 170) else prefs.maxWallpaperChars
        // Pick a section first (Proverbs, Wisdom or the article archive), then a quote within it.
        val r = kotlin.random.Random.nextDouble()
        val pProverb = share(prefs.proverbFreq)
        val pWisdom = share(prefs.wisdomFreq)
        val sectionQuote: Quote? = when {
            r < pProverb -> nextProverb(app, prefs, repo, if (wantWallpaper) maxChars else null)
            r < pProverb + pWisdom -> repo.wisdom().filter { it.category in prefs.wisdomGroups }.let { w ->
                QuotePicker.pick(w, taste, if (wantWallpaper) maxChars else null) ?: QuotePicker.pick(w, taste, null)
            }
            else -> null
        }
        val q = sectionQuote
            ?: (if (pool.isEmpty()) null else QuotePicker.pick(pool, taste, if (wantWallpaper) maxChars else null) ?: QuotePicker.pick(pool, taste, null))
            ?: return@withContext null
        show(app, q, notify = notify && prefs.notificationsOn)
        prefetchProverbs(app)
        q
    }

    /** Share of quote changes given to a section: off, rarely, sometimes, often. */
    fun share(freq: Int): Double = when (freq) { 1 -> 0.10; 2 -> 0.25; 3 -> 0.40; else -> 0.0 }

    /** Builds the lock-screen quote for a Proverbs reading, using ESV text (cached or fetched). */
    suspend fun proverbQuote(app: Context, chunk: ProverbChunk): Quote? {
        val text = EsvClient(app).text(chunk).getOrNull() ?: return null
        val why = com.tim.articlequotes.data.DevotionalWriter(app).significance(chunk, text).orEmpty()
        return Quote(chunk.id, chunk.id, text, "Proverbs", "", "${chunk.ref} (ESV)", "", why, Quote.KIND_PROVERB)
    }

    /**
     * The next reading (in order, or random). Only advances when its text is available.
     * A reading longer than [maxChars] (the lock-screen card limit) is passed over, and the
     * following one tried, so long passages never overflow the card.
     */
    suspend fun nextProverb(app: Context, prefs: Prefs, repo: FeedRepo, maxChars: Int? = null): Quote? {
        val plan = repo.proverbsPlan()
        if (plan.isEmpty()) return null
        val esv = EsvClient(app)
        repeat(4) {
            val idx = if (prefs.proverbOrder == "random") kotlin.random.Random.nextInt(plan.size) else prefs.proverbIndex % plan.size
            val text = esv.text(plan[idx]).getOrNull() ?: return null
            if (prefs.proverbOrder != "random") prefs.proverbIndex = (idx + 1) % plan.size
            if (maxChars == null || text.length <= maxChars) return proverbQuote(app, plan[idx])
        }
        return null
    }

    /** Fetch the next few readings' text ahead of time so the lock screen works offline. */
    suspend fun prefetchProverbs(app: Context, count: Int = 5) {
        val prefs = Prefs(app)
        if (prefs.proverbFreq == 0 || prefs.proverbOrder == "random") return
        val esv = EsvClient(app)
        if (!esv.hasKey()) return
        val plan = FeedRepo(app, prefs).proverbsPlan()
        if (plan.isEmpty()) return
        val writer = com.tim.articlequotes.data.DevotionalWriter(app)
        for (k in 0 until count) {
            val chunk = plan[(prefs.proverbIndex + k) % plan.size]
            val text = esv.text(chunk).getOrNull() ?: break
            writer.significance(chunk, text)  // saved for the card; no-op once written or without a key
        }
    }

    /** Make [q] the current quote (used by "Set as lock screen" on a chosen quote too). */
    suspend fun show(ctx: Context, q: Quote, notify: Boolean) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        prefs.currentQuote = q
        prefs.currentSince = System.currentTimeMillis()
        prefs.markSeen(q.id)
        prefs.pushHistory(q)
        // The notification is silent, so keep it showing the current quote even for in-app changes.
        if (notify || prefs.notificationsOn) Notifications.show(app, q)
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
            val bmp = QuoteCardRenderer.render(q, w, h, prefs.cardStyle, prefs.textScale, showContext = prefs.showContext, position = prefs.lockPosition, contextScale = prefs.contextScale)
            var flags = WallpaperManager.FLAG_LOCK
            if (mode == "both") flags = flags or WallpaperManager.FLAG_SYSTEM
            wm.setBitmap(bmp, null, true, flags)
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper failed: ${e.message}")
        }
    }
}
