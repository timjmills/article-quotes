package com.tim.articlequotes

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.tim.articlequotes.work.Scheduler

class App : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        Scheduler.ensureScheduled(this)
    }
}
