package com.tim.articlequotes.data

import android.content.Context
import android.content.SharedPreferences
import com.tim.articlequotes.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** All user settings and small state. SharedPreferences is plenty for this app. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("aq", Context.MODE_PRIVATE)

    var feedUrl: String
        get() = sp.getString("feedUrl", BuildConfig.DEFAULT_FEED_URL) ?: BuildConfig.DEFAULT_FEED_URL
        set(v) = sp.edit().putString("feedUrl", v.trim().let { if (it.endsWith("/")) it else "$it/" }).apply()

    /** Minutes between new quotes. Default: every 3 hours. */
    var intervalMinutes: Int
        get() = sp.getInt("intervalMinutes", 180)
        set(v) = sp.edit().putInt("intervalMinutes", v.coerceAtLeast(1)).apply()

    var quietStartHour: Int
        get() = sp.getInt("quietStart", 22)
        set(v) = sp.edit().putInt("quietStart", v).apply()

    var quietEndHour: Int
        get() = sp.getInt("quietEnd", 7)
        set(v) = sp.edit().putInt("quietEnd", v).apply()

    var quietEnabled: Boolean
        get() = sp.getBoolean("quietEnabled", true)
        set(v) = sp.edit().putBoolean("quietEnabled", v).apply()

    /** "off" | "lock" | "both" */
    var wallpaperMode: String
        get() = sp.getString("wallpaperMode", "lock") ?: "lock"
        set(v) = sp.edit().putString("wallpaperMode", v).apply()

    var notificationsOn: Boolean
        get() = sp.getBoolean("notificationsOn", true)
        set(v) = sp.edit().putBoolean("notificationsOn", v).apply()

    /** "navy" | "paper" | "forest" | "plum" | "rotate" */
    var cardStyle: String
        get() = sp.getString("cardStyle", "rotate") ?: "rotate"
        set(v) = sp.edit().putString("cardStyle", v).apply()

    var textScale: Float
        get() = sp.getFloat("textScale", 1.0f)
        set(v) = sp.edit().putFloat("textScale", v.coerceIn(0.8f, 2.2f)).apply()

    /** Quotes longer than this are kept for the app only, not the lock screen. */
    var maxWallpaperChars: Int
        get() = sp.getInt("maxWallpaperChars", 320)
        set(v) = sp.edit().putInt("maxWallpaperChars", v.coerceIn(120, 600)).apply()

    /** Show the "why it matters" line on the lock-screen card. */
    /** Where the quote sits on the lock screen: "middle" or "lower" (for phones with a big centred clock). */
    var lockPosition: String
        get() = sp.getString("lockPosition", "middle") ?: "middle"
        set(v) = sp.edit().putString("lockPosition", v).apply()

    var showContext: Boolean
        get() = sp.getBoolean("showContext", true)
        set(v) = sp.edit().putBoolean("showContext", v).apply()

    var unmeteredOnly: Boolean
        get() = sp.getBoolean("unmeteredOnly", true)
        set(v) = sp.edit().putBoolean("unmeteredOnly", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    var lastSyncMessage: String
        get() = sp.getString("lastSyncMessage", "") ?: ""
        set(v) = sp.edit().putString("lastSyncMessage", v).apply()

    /** Which article types feed the quotes. The switches in Settings edit this. */
    var categories: Set<String>
        get() = sp.getStringSet("categories", null)?.toSet() ?: Categories.ALL.toSet()
        set(v) = sp.edit().putStringSet("categories", v.toSet()).apply()

    var currentQuote: Quote?
        get() = sp.getString("currentQuote", null)?.let { runCatching { Quote.fromJson(JSONObject(it)) }.getOrNull() }
        set(v) = sp.edit().putString("currentQuote", v?.toJson()?.toString()).apply()

    var currentSince: Long
        get() = sp.getLong("currentSince", 0L)
        set(v) = sp.edit().putLong("currentSince", v).apply()

    // ---- history: every quote that has been shown, so you can move back and forth ----
    val history: List<Quote>
        get() {
            val raw = sp.getString("history", "[]") ?: "[]"
            val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            return List(arr.length()) { Quote.fromJson(arr.getJSONObject(it)) }
        }

    var historyIndex: Int
        get() = sp.getInt("historyIndex", -1)
        set(v) = sp.edit().putInt("historyIndex", v).apply()

    /** Append [q] after the current position (dropping any "forward" entries) and move to it. */
    fun pushHistory(q: Quote) {
        val list = history.toMutableList()
        val idx = historyIndex
        if (idx in list.indices && list[idx].id == q.id) return
        if (idx >= 0 && idx < list.size - 1) { while (list.size > idx + 1) list.removeAt(list.size - 1) }
        list.add(q)
        while (list.size > 300) list.removeAt(0)
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("history", arr.toString()).putInt("historyIndex", list.size - 1).apply()
    }

    // ---- seen quotes (rolling window so the pool never runs dry) ----
    val seenIds: Set<String>
        get() = sp.getStringSet("seen", emptySet())?.toSet() ?: emptySet()

    fun markSeen(id: String) {
        val cur = (sp.getStringSet("seen", emptySet()) ?: emptySet()).toMutableList()
        cur.remove(id); cur.add(id)
        while (cur.size > 3000) cur.removeAt(0)
        sp.edit().putStringSet("seen", cur.toSet()).apply()
    }

    // ---- favourites ----
    val favorites: List<Quote>
        get() {
            val raw = sp.getString("favorites", "[]") ?: "[]"
            val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            return List(arr.length()) { Quote.fromJson(arr.getJSONObject(it)) }
        }

    fun isFavorite(id: String) = favorites.any { it.id == id }

    fun toggleFavorite(q: Quote): Boolean {
        val list = favorites.toMutableList()
        val existing = list.indexOfFirst { it.id == q.id }
        val nowFav = if (existing >= 0) { list.removeAt(existing); false } else { list.add(0, q); true }
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        val at = runCatching { JSONObject(sp.getString("favSavedAt", "{}") ?: "{}") }.getOrElse { JSONObject() }
        if (nowFav) at.put(q.id, System.currentTimeMillis()) else at.remove(q.id)
        sp.edit().putString("favSavedAt", at.toString()).apply()
        if (nowFav) nudgeAuthor(q.author, 1)
        sp.edit().putString("favorites", arr.toString()).apply()
        return nowFav
    }

    // ---- screen grace: don't swap the quote right after the screen goes dark ----
    /** Seconds after the screen turns off during which the quote stays put. 0 = off. */
    var graceSeconds: Int
        get() = sp.getInt("graceSeconds", 30)
        set(v) = sp.edit().putInt("graceSeconds", v.coerceIn(0, 600)).apply()

    /** When the app process last started; screen events before this were not observed. */
    var processStart: Long
        get() = sp.getLong("processStart", 0L)
        set(v) = sp.edit().putLong("processStart", v).apply()

    /** A timed change was skipped because the screen was on; do it once the screen goes dark. */
    var pendingChange: Boolean
        get() = sp.getBoolean("pendingChange", false)
        set(v) = sp.edit().putBoolean("pendingChange", v).apply()

    var lastScreenOff: Long
        get() = sp.getLong("lastScreenOff", 0L)
        set(v) = sp.edit().putLong("lastScreenOff", v).apply()

    var lastScreenOn: Long
        get() = sp.getLong("lastScreenOn", 0L)
        set(v) = sp.edit().putLong("lastScreenOn", v).apply()

    // ---- taste: "more like this" / "less like this", muted authors, category mix ----
    private fun intMap(key: String): MutableMap<String, Int> {
        val o = runCatching { JSONObject(sp.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val m = HashMap<String, Int>(); o.keys().forEach { m[it] = o.optInt(it) }; return m
    }
    private fun putIntMap(key: String, m: Map<String, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> if (v != 0) o.put(k, v) }
        sp.edit().putString(key, o.toString()).apply()
    }

    /** Author score from -3 to +3, nudged by the like/less buttons and by saving a quote. */
    val authorScores: Map<String, Int> get() = intMap("authorScores")
    fun nudgeAuthor(author: String, by: Int) {
        val m = intMap("authorScores"); m[author] = ((m[author] ?: 0) + by).coerceIn(-3, 3); putIntMap("authorScores", m)
    }

    /** Article score from -2 to +2 (more/less like this applies to the whole article). */
    val articleScores: Map<String, Int> get() = intMap("articleScores")
    fun nudgeArticle(id: String, by: Int) {
        val m = intMap("articleScores"); m[id] = ((m[id] ?: 0) + by).coerceIn(-2, 2); putIntMap("articleScores", m)
    }

    val mutedAuthors: Set<String> get() = sp.getStringSet("mutedAuthors", emptySet())?.toSet() ?: emptySet()
    fun setMuted(author: String, muted: Boolean) {
        val s = mutedAuthors.toMutableSet(); if (muted) s.add(author) else s.remove(author)
        sp.edit().putStringSet("mutedAuthors", s).apply()
    }

    /** How much of each category: 0 = less, 1 = normal, 2 = more. */
    fun categoryWeight(c: String): Int = sp.getInt("catw:$c", 1)
    fun setCategoryWeight(c: String, w: Int) = sp.edit().putInt("catw:$c", w.coerceIn(0, 2)).apply()

    /** Leadership and education in the morning, family in the evening. */
    var timeOfDayThemes: Boolean
        get() = sp.getBoolean("todThemes", true)
        set(v) = sp.edit().putBoolean("todThemes", v).apply()

    // ---- reading loop ----
    val readArticles: Set<String> get() = sp.getStringSet("readArticles", emptySet())?.toSet() ?: emptySet()
    fun markRead(id: String) {
        if (id in readArticles) return
        val list = (sp.getString("readOrder", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
        list.remove(id); list.add(id); while (list.size > 2000) list.removeAt(0)
        sp.edit().putStringSet("readArticles", list.toSet()).putString("readOrder", list.joinToString(",")).apply()
    }

    /** Articles saved for later: JSON list of {id, title, author, category, date, savedAt}. */
    val readLater: List<JSONObject>
        get() {
            val arr = runCatching { JSONArray(sp.getString("readLater", "[]") ?: "[]") }.getOrElse { JSONArray() }
            return List(arr.length()) { arr.getJSONObject(it) }
        }
    fun isReadLater(id: String) = readLater.any { it.optString("id") == id }
    fun toggleReadLater(a: ArticleDetail): Boolean {
        val list = readLater.toMutableList()
        val i = list.indexOfFirst { it.optString("id") == a.id }
        val now = if (i >= 0) { list.removeAt(i); false } else {
            list.add(0, JSONObject().put("id", a.id).put("title", a.title).put("author", a.author)
                .put("category", a.category).put("date", a.date).put("savedAt", System.currentTimeMillis())); true
        }
        val arr = JSONArray(); list.forEach { arr.put(it) }
        sp.edit().putString("readLater", arr.toString()).apply()
        return now
    }

    fun setReadLater(list: List<JSONObject>) {
        val arr = JSONArray(); list.forEach { arr.put(it) }
        sp.edit().putString("readLater", arr.toString()).apply()
    }

    /** When each favourite was saved (quote id -> millis), for the weekly review. */
    val favoriteSavedAt: Map<String, Long>
        get() {
            val o = runCatching { JSONObject(sp.getString("favSavedAt", "{}") ?: "{}") }.getOrElse { JSONObject() }
            val m = HashMap<String, Long>(); o.keys().forEach { m[it] = o.optLong(it) }; return m
        }

    var weeklyReviewOn: Boolean
        get() = sp.getBoolean("weeklyReview", true)
        set(v) = sp.edit().putBoolean("weeklyReview", v).apply()

    /** Set once a "no new articles" notification has gone out, so it isn't repeated daily. */
    var staleNotified: Boolean
        get() = sp.getBoolean("staleNotified", false)
        set(v) = sp.edit().putBoolean("staleNotified", v).apply()

    // ---- app updates ----
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var availableUpdate: String
        get() = sp.getString("availableUpdate", "") ?: ""
        set(v) = sp.edit().putString("availableUpdate", v).apply()

    fun inQuietHours(now: Calendar = Calendar.getInstance()): Boolean {
        if (!quietEnabled) return false
        val h = now.get(Calendar.HOUR_OF_DAY)
        val s = quietStartHour; val e = quietEndHour
        if (s == e) return false
        return if (s < e) h in s until e else (h >= s || h < e)
    }
}
