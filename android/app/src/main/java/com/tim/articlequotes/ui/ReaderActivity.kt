package com.tim.articlequotes.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs

/**
 * The quote reader that opens over the lock screen, without unlocking.
 *
 * Launched by tapping the quote notification on the lock screen, or from the "Quote reader"
 * icon (which Samsung's Good Lock / LockStar can place on the lock screen). While it is open
 * the screen stays on, so a quote can be read to the end. Saving, moving between quotes and
 * reading the article summary all work here; only opening the full article in the browser
 * asks to unlock.
 */
class ReaderActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()
        setContent { ReaderRoot(this, onClose = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        keepScreenOn()
        Prefs(this).readerSeenAt = System.currentTimeMillis()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        keepScreenOn()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(releaseScreen)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reopened.intValue++  // back to the current quote, closing any open summary
    }

    private var stoppedAt = 0L

    override fun onStart() {
        super.onStart()
        // The reader survives the screen going dark, so waking the phone brings the same quote
        // back. Only after a long time away does it step aside for the normal lock screen.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > KEEP_WHILE_DARK_MS) { finish(); return }
        stoppedAt = 0
        Prefs(this).readerSeenAt = System.currentTimeMillis()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        Prefs(this).readerSeenAt = stoppedAt
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Prefs(this).readerSeenAt = 0L
    }

    /** Hold the screen on while reading; let it sleep after [Prefs.readerMinutes] without a touch. */
    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(releaseScreen)
        handler.postDelayed(releaseScreen, Prefs(this).readerMinutes * 60_000L)
    }

    companion object {
        /** Bumped when the reader is re-opened while already alive. */
        val reopened = androidx.compose.runtime.mutableIntStateOf(0)

        /** How long the reader stays waiting behind a dark screen. */
        const val KEEP_WHILE_DARK_MS = 30 * 60_000L

        fun intent(ctx: Context): Intent = Intent(ctx, ReaderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Ask to unlock (if locked), then run [then]. Used for things that can't show over the lock screen. */
        fun unlockThen(activity: Activity, then: () -> Unit) {
            val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!km.isKeyguardLocked) { then(); return }
            km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = then()
            })
        }
    }
}

@androidx.compose.runtime.Composable
private fun ReaderRoot(activity: Activity, onClose: () -> Unit) {
    val ctx = activity.applicationContext
    val prefs = remember { Prefs(ctx) }
    val repo = remember { FeedRepo(ctx, prefs) }
    val scope = rememberCoroutineScope()
    val nav = remember { QuoteNav(ctx, prefs, repo, scope) }
    var textScale by remember { mutableFloatStateOf(prefs.textScale) }
    var article by rememberSaveable { mutableStateOf<String?>(null) }
    val reopened = ReaderActivity.reopened.intValue
    androidx.compose.runtime.LaunchedEffect(reopened) { article = null; nav.refresh() }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { nav.refresh(); onPauseOrDispose { } }
    val openUrl: (String) -> Unit = { url ->
        ReaderActivity.unlockThen(activity) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    ArticleQuotesTheme(textScale) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val a = article
            if (a != null) {
                BackHandler { article = null }
                ArticleScreen(a, repo, prefs, onBack = { article = null }, openUrl = openUrl)
            } else {
                FullScreenQuote(
                    nav, prefs, textScale,
                    onTextScale = { textScale = it },
                    onOpen = { article = it },
                    onClose = onClose,
                    closeHint = "Close reader",
                    matchLockScreen = true,
                )
            }
        }
    }
}
