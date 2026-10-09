package com.tim.articlequotes.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.tim.articlequotes.data.Categories
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Share a quote as a picture of its card, or export saved quotes as a document. */
object Sharing {
    private fun sharedDir(ctx: Context) = File(ctx.cacheDir, "shared").apply { mkdirs() }

    private fun uriFor(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Renders the quote card at a social-friendly 4:5 size and opens the share sheet. */
    suspend fun shareQuoteImage(ctx: Context, q: Quote, prefs: Prefs) {
        val file = withContext(Dispatchers.Default) {
            val bmp = QuoteCardRenderer.render(q, 1080, 1350, prefs.cardStyle, 1.0f, preview = true, showContext = prefs.showContext)
            val f = File(sharedDir(ctx), "quote.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            putExtra(Intent.EXTRA_TEXT, "“${q.text}”\n— ${q.author}, ${q.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share quote image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Writes all saved quotes to a text document and opens the share sheet.
     * Choose Drive to save it to Google Drive; Google Docs opens it directly.
     */
    suspend fun exportSaved(ctx: Context, prefs: Prefs) {
        val favs = prefs.favorites
        val savedAt = prefs.favoriteSavedAt
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.append("Saved quotes — Article Quotes\nExported $day · ${favs.size} quote${if (favs.size == 1) "" else "s"}\n\n")
            favs.groupBy { it.category }.toSortedMap().forEach { (cat, qs) ->
                sb.append("=== ").append(Categories.short(cat)).append(" ===\n\n")
                qs.forEach { q ->
                    sb.append("“").append(q.text).append("”\n")
                    sb.append("— ").append(q.author).append(", ").append(q.title)
                    if (q.date.isNotBlank()) sb.append(" (").append(q.date).append(")")
                    sb.append("\n")
                    if (q.context.isNotBlank()) sb.append("Why it matters: ").append(q.context).append("\n")
                    savedAt[q.id]?.let { sb.append("Saved ").append(SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it))).append("\n") }
                    sb.append("\n")
                }
            }
            File(sharedDir(ctx), "Saved quotes $day.txt").also { it.writeText(sb.toString()) }
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            putExtra(Intent.EXTRA_SUBJECT, "Saved quotes $day")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Export saved quotes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
