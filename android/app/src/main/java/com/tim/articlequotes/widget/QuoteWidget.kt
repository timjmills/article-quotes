package com.tim.articlequotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tim.articlequotes.R
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.ui.MainActivity

/** Home-screen widget showing the current quote. Tap opens the article summary. */
class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHANGED) refresh(context)
    }

    companion object {
        const val ACTION_CHANGED = "com.tim.articlequotes.QUOTE_CHANGED"

        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, QuoteWidget::class.java))
            ids.forEach { update(ctx, m, it) }
        }

        private fun update(ctx: Context, m: AppWidgetManager, id: Int) {
            val q = Prefs(ctx).currentQuote
            val views = RemoteViews(ctx.packageName, R.layout.widget_quote)
            if (q != null) {
                val size = when {
                    q.text.length <= 120 -> 18f
                    q.text.length <= 220 -> 16f
                    else -> 14f
                }
                views.setTextViewTextSize(R.id.widget_quote, android.util.TypedValue.COMPLEX_UNIT_SP, size)
                views.setTextViewText(R.id.widget_quote, "“${q.text}”")
                views.setTextViewText(R.id.widget_author, "— ${q.author} · ${q.title}")
            }
            val open = Intent(ctx, MainActivity::class.java).apply {
                q?.let { putExtra(MainActivity.EXTRA_ARTICLE, it.articleId) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 10, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, views)
        }
    }
}
