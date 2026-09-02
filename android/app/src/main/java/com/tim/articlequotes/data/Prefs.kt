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
        set(v) = sp.edit().putInt("intervalMinutes", v.coerceAtLeast(15)).apply()

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
        set(v) = sp.edit().putFloat("textScale", v.coerceIn(0.8f, 1.6f)).apply()

    /** Quotes longer than this are kept for the app only, not the lock screen. */
    var maxWallpaperChars: Int
        get() = sp.getInt("maxWallpaperChars", 320)
        set(v) = sp.edit().putInt("maxWallpaperChars", v.coerceIn(120, 600)).apply()

    /** Show the "why it matters" line on the lock-screen card. */
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
        sp.edit().putString("favorites", arr.toString()).apply()
        return nowFav
    }

    fun inQuietHours(now: Calendar = Calendar.getInstance()): Boolean {
        if (!quietEnabled) return false
        val h = now.get(Calendar.HOUR_OF_DAY)
        val s = quietStartHour; val e = quietEndHour
        if (s == e) return false
        return if (s < e) h in s until e else (h >= s || h < e)
    }
}
