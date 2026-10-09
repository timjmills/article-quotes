package com.tim.articlequotes

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.tim.articlequotes.work.ScreenStateReceiver
import com.tim.articlequotes.data.Prefs
import androidx.work.Configuration
import com.tim.articlequotes.work.Scheduler

class App : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        Prefs(this).processStart = System.currentTimeMillis()
        Notifications.ensureChannel(this)
        Scheduler.ensureScheduled(this)
        // Screen on/off can only be heard by a receiver registered at runtime.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, ScreenStateReceiver(), filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
