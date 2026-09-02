package com.tim.articlequotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tim.articlequotes.work.Scheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.ensureScheduled(context)
        }
    }
}
