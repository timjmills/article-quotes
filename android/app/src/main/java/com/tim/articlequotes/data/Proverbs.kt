package com.tim.articlequotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** One reading from the Proverbs plan: 1-3 verses. References only; the text comes from the ESV API. */
data class ProverbChunk(val id: String, val ref: String, val query: String, val chapter: Int, val v1: Int, val v2: Int) {
    val verses: Int get() = v2 - v1 + 1
}

/**
 * ESV text, fetched with Tim's personal key from api.esv.org and cached on the phone.
 *
 * The ESV API terms allow an app to keep at most 500 verses, so the cache holds at most
 * [MAX_VERSES] verses and drops the least recently used readings first. The cache folder is
 * excluded from Android backup. The text is never written to the public feed.
 */
class EsvClient(ctx: Context) {
    private val dir = File(ctx.applicationContext.filesDir, "esv").apply { mkdirs() }
    private val secrets = SecretStore(ctx)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun hasKey() = secrets.has(SecretStore.ESV)

    private fun file(c: ProverbChunk) = File(dir, "${c.id}.txt")

    /** Cached text, or null. Reading it marks it as recently used. */
    fun cached(c: ProverbChunk): String? {
        val f = file(c)
        if (!f.exists()) return null
        f.setLastModified(System.currentTimeMillis())
        return f.readText().trim().takeIf { it.isNotBlank() }
    }

    /** Fetches (or returns cached) text. Returns null when there is no key or no connection. */
    suspend fun text(c: ProverbChunk): Result<String> = withContext(Dispatchers.IO) {
        cached(c)?.let { return@withContext Result.success(it) }
        val key = secrets.get(SecretStore.ESV) ?: return@withContext Result.failure(IllegalStateException("No ESV key yet. Add it in Settings."))
        val url = "https://api.esv.org/v3/passage/text/".toHttpUrl().newBuilder()
            .addQueryParameter("q", c.query)
            .addQueryParameter("include-passage-references", "false")
            .addQueryParameter("include-verse-numbers", "false")
            .addQueryParameter("include-first-verse-numbers", "false")
            .addQueryParameter("include-footnotes", "false")
            .addQueryParameter("include-footnote-body", "false")
            .addQueryParameter("include-headings", "false")
            .addQueryParameter("include-short-copyright", "false")
            .addQueryParameter("include-passage-horizontal-lines", "false")
            .addQueryParameter("include-heading-horizontal-lines", "false")
            .addQueryParameter("indent-poetry", "false")
            .build()
        runCatching {
            http.newCall(Request.Builder().url(url).header("Authorization", "Token $key").build()).execute().use { r ->
                if (r.code == 401 || r.code == 403) error("The ESV key was not accepted. Check it in Settings.")
                if (!r.isSuccessful) error("ESV API error ${r.code}")
                val passages = JSONObject(r.body?.string().orEmpty()).optJSONArray("passages")
                val text = passages?.optString(0).orEmpty().replace(Regex("\\s+"), " ").trim()
                if (text.isEmpty()) error("ESV returned no text for ${c.ref}")
                // Write then rename, so a reader never sees half a file.
                val tmp = File(dir, "${c.id}.tmp"); tmp.writeText(text)
                if (!tmp.renameTo(file(c))) { file(c).delete(); tmp.renameTo(file(c)) }
                trim()
                text
            }
        }
    }

    /** ESV text for any short reference (cross references in devotionals). Same cache and verse limit. */
    suspend fun textForRef(ref: String): Result<String> {
        // Only "Book C:V" or "Book C:V-V" within one chapter; anything else (whole chapters,
        // cross-chapter ranges, lists) is not fetched, so the verse count stays exact.
        val m = Regex("""^(.+?)\s+(\d+):(\d+)(?:\s*[-–]\s*(\d+))?$""").find(ref.trim())
            ?: return Result.failure(IllegalArgumentException("Not a short passage"))
        val v1 = m.groupValues[3].toInt()
        val v2 = m.groupValues[4].toIntOrNull() ?: v1
        if (v2 < v1 || v2 - v1 > 3) return Result.failure(IllegalArgumentException("Passage too long to print"))
        val slug = ref.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        // Encode the verse count in the file name so the cap counts it correctly.
        val chunk = ProverbChunk("ref-$slug-$v1-$v2", ref, ref, m.groupValues[2].toInt(), v1, v2)
        return text(chunk).map { it.replace(Regex("""\s*\(ESV\)\s*$"""), "") }
    }

    /** Keep the cache within the ESV limit, dropping the least recently used readings. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.name.endsWith(".txt") }?.sortedBy { it.lastModified() } ?: return
        var verses = files.sumOf { versesIn(it.name) }
        for (f in files) {
            if (verses <= MAX_VERSES) break
            verses -= versesIn(f.name); f.delete()
        }
    }

    private fun versesIn(name: String): Int {
        val p = name.removeSuffix(".txt").split("-")  // prov-<ch>-<v1>-<v2> or ref-<slug>-<v1>-<v2>
        return runCatching { p[p.size - 1].toInt() - p[p.size - 2].toInt() + 1 }.getOrDefault(2)
    }

    fun cachedVerseCount(): Int = dir.listFiles()?.filter { it.name.endsWith(".txt") }?.sumOf { versesIn(it.name) } ?: 0

    /**
     * 400 here leaves room under ESV's 500 for readings kept in history (at most
     * [Prefs.MAX_PROVERBS_IN_HISTORY]) and saved favourites.
     */
    companion object { const val MAX_VERSES = 400 }
}
