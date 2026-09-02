package com.tim.articlequotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class SyncResult(val ok: Boolean, val message: String, val changedShards: Int = 0)

/**
 * Downloads the static feed published by the PC-side builder and keeps a local copy.
 * The manifest lists quote shards with hashes, so a daily sync only fetches what changed.
 */
class FeedRepo(ctx: Context, private val prefs: Prefs) {
    private val app = ctx.applicationContext
    private val root = File(app.filesDir, "feed").apply { mkdirs() }
    private val quotesDir = File(root, "quotes").apply { mkdirs() }
    private val articlesDir = File(root, "articles").apply { mkdirs() }
    private val manifestFile = File(root, "manifest.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun fetch(url: String): String? {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()
        }
    }

    fun hasData(): Boolean = manifestFile.exists() && (quotesDir.listFiles()?.isNotEmpty() == true)

    fun manifest(): JSONObject? = runCatching { JSONObject(manifestFile.readText()) }.getOrNull()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val base = prefs.feedUrl
        val text = try { fetch(base + "manifest.json") } catch (e: Exception) { null }
            ?: return@withContext SyncResult(false, "Could not reach the feed. Check your connection or the feed address.")
        val remote = runCatching { JSONObject(text) }.getOrNull()
            ?: return@withContext SyncResult(false, "The feed manifest was not valid JSON.")
        val local = manifest()
        val localShas = HashMap<String, String>()
        local?.optJSONArray("shards")?.let { a ->
            for (i in 0 until a.length()) { val s = a.getJSONObject(i); localShas[s.getString("name")] = s.optString("sha") }
        }
        val shards = remote.optJSONArray("shards") ?: JSONArray()
        val keep = HashSet<String>()
        var changed = 0
        for (i in 0 until shards.length()) {
            val s = shards.getJSONObject(i)
            val name = s.getString("name"); val sha = s.optString("sha")
            val f = File(quotesDir, "$name.json")
            keep.add(f.name)
            if (f.exists() && localShas[name] == sha) continue
            val body = try { fetch(base + s.getString("path")) } catch (e: Exception) { null }
                ?: return@withContext SyncResult(false, "Download stopped part-way (shard $name). Try again.", changed)
            val tmp = File(quotesDir, "$name.json.tmp")
            tmp.writeText(body); tmp.renameTo(f)
            changed++
        }
        quotesDir.listFiles()?.forEach { if (it.name.endsWith(".json") && it.name !in keep) it.delete() }
        manifestFile.writeText(remote.toString())
        cache = null
        prefs.lastSync = System.currentTimeMillis()
        val msg = "${remote.optInt("articleCount")} articles · ${remote.optInt("quoteCount")} quotes"
        prefs.lastSyncMessage = msg
        SyncResult(true, msg, changed)
    }

    @Volatile private var cache: List<Quote>? = null

    /** Every quote in the local shards (all categories). Cached in memory. */
    fun allQuotes(): List<Quote> {
        cache?.let { return it }
        val out = ArrayList<Quote>(20000)
        quotesDir.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.name }?.forEach { f ->
            val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return@forEach
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val aid = a.getString("id")
                val qs = a.optJSONArray("q") ?: continue
                val xs = a.optJSONArray("x")
                for (j in 0 until qs.length()) {
                    val ctx = if (xs != null && j < xs.length()) xs.optString(j) else ""
                    out.add(Quote("$aid:$j", aid, qs.getString(j), a.optString("c"), a.optString("t"), a.optString("a"), a.optString("d"), ctx))
                }
            }
        }
        cache = out
        return out
    }

    fun quotesFor(categories: Set<String>): List<Quote> = allQuotes().filter { it.category in categories }

    fun articles(categories: Set<String>? = null): List<ArticleSummary> {
        val byId = LinkedHashMap<String, ArticleSummary>()
        for (q in allQuotes()) {
            if (categories != null && q.category !in categories) continue
            val cur = byId[q.articleId]
            byId[q.articleId] = if (cur == null) ArticleSummary(q.articleId, q.title, q.author, q.category, q.date, 1)
            else cur.copy(quoteCount = cur.quoteCount + 1)
        }
        return byId.values.sortedByDescending { it.date }
    }

    suspend fun article(id: String): ArticleDetail? = withContext(Dispatchers.IO) {
        val f = File(articlesDir, "$id.json")
        val text = if (f.exists()) f.readText() else {
            val body = try { fetch(prefs.feedUrl + "articles/$id.json") } catch (e: Exception) { null }
            body?.also { f.writeText(it) }
        } ?: return@withContext null
        runCatching { ArticleDetail.fromJson(JSONObject(text)) }.getOrNull()
    }
}
