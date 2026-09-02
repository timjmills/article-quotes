package com.tim.articlequotes.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tim.articlequotes.data.Categories
import com.tim.articlequotes.data.Quote
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a lock-screen wallpaper for one quote.
 *
 * Layout rules (tuned for a 1080x2400 phone, scaled for others):
 *  - keep the top ~30% clear for the clock and the bottom ~17% for shortcuts;
 *  - text size starts from the quote length and shrinks until it fits the safe area;
 *  - a big accent quotation mark, then the quote in a serif face, then author/title.
 */
object QuoteCardRenderer {
    data class Palette(val name: String, val top: Int, val bottom: Int, val text: Int, val sub: Int, val accent: Int)

    val palettes = listOf(
        Palette("navy", 0xFF16213E.toInt(), 0xFF0B1020.toInt(), 0xFFF4F1EA.toInt(), 0xFFB9B3A6.toInt(), 0xFFE0B04A.toInt()),
        Palette("paper", 0xFFF6F1E7.toInt(), 0xFFE9E1D0.toInt(), 0xFF1E1B16.toInt(), 0xFF5C554A.toInt(), 0xFFB5462F.toInt()),
        Palette("forest", 0xFF1C3A2E.toInt(), 0xFF0D1F18.toInt(), 0xFFF1F5EE.toInt(), 0xFFB6C6B9.toInt(), 0xFFD9A441.toInt()),
        Palette("plum", 0xFF3A1F3E.toInt(), 0xFF1A0E1E.toInt(), 0xFFF7F0F5.toInt(), 0xFFC9B7C6.toInt(), 0xFFF0A868.toInt()),
    )

    fun paletteFor(style: String, q: Quote): Palette =
        palettes.firstOrNull { it.name == style } ?: palettes[abs(q.articleId.hashCode()) % palettes.size]

    fun render(q: Quote, width: Int, height: Int, style: String, textScale: Float, preview: Boolean = false, showContext: Boolean = true): Bitmap {
        val w = max(width, 480); val h = max(height, 800)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = paletteFor(style, q)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p.top, p.bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val scale = w / 1080f
        // Safe area: previews use the whole card; real wallpapers dodge the clock and shortcuts.
        val left = w * 0.09f
        val areaW = (w * 0.82f).toInt()
        val areaTop = if (preview) h * 0.12f else h * 0.30f
        val areaBottom = if (preview) h * 0.90f else h * 0.83f
        val areaH = areaBottom - areaTop

        val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val len = q.text.length
        var size = scale * textScale * when {
            len <= 80 -> 74f
            len <= 140 -> 64f
            len <= 220 -> 56f
            len <= 320 -> 48f
            len <= 450 -> 42f
            else -> 36f
        }
        val minSize = 28f * scale
        val subSize = 30f * scale * textScale
        val markSize = 150f * scale
        val quoteText = "“${q.text}”"

        // Optional "why it matters" line (max 3 lines) under the attribution.
        val ctxPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.sub; typeface = sans; textSize = subSize * 0.82f }
        val ctxLayout: StaticLayout? = if (showContext && q.context.isNotBlank()) {
            StaticLayout.Builder.obtain(q.context, 0, q.context.length, ctxPaint, areaW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f)
                .setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(false).build()
        } else null
        val attributionBlock = subSize * 4.2f + (ctxLayout?.let { it.height + subSize * 0.6f } ?: 0f)

        var layout: StaticLayout
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; typeface = serif }
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(quoteText, 0, quoteText.length, tp, areaW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
            val need = markSize * 0.55f + layout.height + attributionBlock
            if (need <= areaH || size <= minSize) break
            size *= 0.93f
        }

        // Vertical centering inside the safe area
        val contentH = markSize * 0.55f + layout.height + attributionBlock
        var y = areaTop + max(0f, (areaH - contentH) / 2f)

        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; typeface = serif; textSize = markSize; alpha = 210 }
        c.drawText("“", left - 6f * scale, y + markSize * 0.72f, mark)
        y += markSize * 0.55f

        c.save(); c.translate(left, y); layout.draw(c); c.restore()
        y += layout.height + subSize * 1.3f

        val rule = Paint().apply { color = p.accent; strokeWidth = 4f * scale }
        c.drawLine(left, y, left + 90f * scale, y, rule)
        y += subSize * 1.1f

        val author = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; typeface = sansBold; textSize = subSize }
        c.drawText(ellipsize(q.author, author, areaW.toFloat()), left, y + subSize, author)
        y += subSize * 1.45f
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.sub; typeface = sans; textSize = subSize * 0.9f }
        c.drawText(ellipsize(q.title, title, areaW.toFloat()), left, y + subSize * 0.9f, title)
        y += subSize * 1.4f
        val cat = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; typeface = sansBold; textSize = subSize * 0.7f; letterSpacing = 0.12f }
        c.drawText(Categories.short(q.category).uppercase(), left, y + subSize * 0.7f, cat)
        if (ctxLayout != null) {
            y += subSize * 1.3f
            c.save(); c.translate(left, y); ctxLayout.draw(c); c.restore()
        }
        return bmp
    }

    private fun ellipsize(s: String, paint: TextPaint, maxW: Float): String {
        if (paint.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 4 && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Small helper for the in-app preview: same card at a fraction of the size. */
    fun preview(q: Quote, style: String, textScale: Float, width: Int, showContext: Boolean = true): Bitmap =
        render(q, width, (width * 1.6f).toInt(), style, textScale, preview = true, showContext = showContext)

    fun contrastOn(color: Int): Int = if (Color.luminance(color) > 0.5) Color.BLACK else Color.WHITE

    fun clampScale(v: Float) = min(1.6f, max(0.8f, v))
}
