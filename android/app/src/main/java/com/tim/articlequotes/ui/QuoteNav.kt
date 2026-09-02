package com.tim.articlequotes.ui

import android.content.Context
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.work.Rotator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared "which quote am I looking at" state for the Today screen and the full-screen
 * view: the current quote, its position in the history, and the moves between quotes.
 */
class QuoteNav(
    private val ctx: Context,
    private val prefs: Prefs,
    private val repo: FeedRepo,
    private val scope: CoroutineScope,
) {
    var quote: Quote? by mutableStateOf(prefs.currentQuote); private set
    var fav by mutableStateOf(quote?.let { prefs.isFavorite(it.id) } ?: false); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var hasData by mutableStateOf(repo.hasData()); private set
    var histIndex by mutableIntStateOf(prefs.historyIndex); private set
    var histSize by mutableIntStateOf(prefs.history.size); private set
    private var wallpaperJob: Job? = null

    val hasPrevious: Boolean get() = histIndex > 0
    val hasNext: Boolean get() = histIndex < histSize - 1

    fun refresh() {
        quote = prefs.currentQuote
        fav = quote?.let { prefs.isFavorite(it.id) } ?: false
        hasData = repo.hasData()
        histIndex = prefs.historyIndex
        histSize = prefs.history.size
    }

    /** Re-render the lock screen after a short pause, so flicking through quotes stays smooth. */
    fun applyWallpaperSoon() {
        val q = quote ?: return
        wallpaperJob?.cancel()
        wallpaperJob = scope.launch {
            delay(900)
            withContext(Dispatchers.IO) { Rotator.applyWallpaper(ctx, prefs, q) }
        }
    }

    fun newQuote() {
        if (busy) return
        busy = true; status = ""
        scope.launch {
            val q = Rotator.rotate(ctx, notify = false, respectQuietHours = false)
            refresh()
            if (q == null) status = "No quotes yet. Connect to Wi-Fi and tap Download."
            busy = false
        }
    }

    fun goTo(index: Int) {
        val q = Rotator.showFromHistory(ctx, index) ?: return
        quote = q; fav = prefs.isFavorite(q.id); histIndex = index
        applyWallpaperSoon()
    }

    fun previous() { if (hasPrevious) goTo(histIndex - 1) }
    fun next() { if (hasNext) goTo(histIndex + 1) else newQuote() }

    fun toggleFavorite() {
        val q = quote ?: return
        fav = prefs.toggleFavorite(q)
    }
}

/** Swipe left for the next quote, right for the previous one. */
fun Modifier.quoteSwipe(nav: QuoteNav, thresholdPx: Float): Modifier =
    pointerInput(nav.histIndex, nav.histSize, nav.busy) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onDragEnd = { if (drag <= -thresholdPx) nav.next() else if (drag >= thresholdPx) nav.previous() },
            onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
        )
    }
