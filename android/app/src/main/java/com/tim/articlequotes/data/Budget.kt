package com.tim.articlequotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** What the Claude API budget looks like this month. */
data class BudgetView(
    val allowance: Double,
    val appSpent: Double,
    val orgSpent: Double?,     // null without an Admin API key
    val orgError: String?,
    val periodStart: LocalDate,
) {
    val spent: Double get() = orgSpent ?: appSpent
    val left: Double get() = (allowance - spent).coerceAtLeast(0.0)
}

/**
 * Tracks API spend against a monthly allowance (Tim's is $200).
 *
 * There is no API that returns an account's remaining balance with a normal key, so this
 * combines (1) this app's own spend, always available, recorded from each response's token
 * usage, and (2) optionally, the whole organization's month-to-date cost from the Admin API
 * cost report, which needs an Admin API key.
 */
class Budget(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("budget", Context.MODE_PRIVATE)
    private val secrets = SecretStore(ctx)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    var allowance: Double
        get() = sp.getFloat("allowance", 200f).toDouble()
        set(v) = sp.edit().putFloat("allowance", v.toFloat().coerceAtLeast(0f)).apply()

    /** Day of the month the allowance resets (1-28). */
    var resetDay: Int
        get() = sp.getInt("resetDay", 1)
        set(v) = sp.edit().putInt("resetDay", v.coerceIn(1, 28)).apply()

    fun periodStart(today: LocalDate = LocalDate.now()): LocalDate {
        val thisMonth = today.withDayOfMonth(resetDay)
        return if (!today.isBefore(thisMonth)) thisMonth else thisMonth.minusMonths(1)
    }

    private fun periodKey() = "spent:" + periodStart()

    fun record(costUsd: Double) = synchronized(LOCK) {
        val k = periodKey()
        sp.edit().putFloat(k, sp.getFloat(k, 0f) + costUsd.toFloat()).commit()
        Unit
    }

    fun appSpent(): Double = sp.getFloat(periodKey(), 0f).toDouble()

    fun hasAdminKey() = secrets.has(SecretStore.ADMIN)

    /** Month-to-date organization cost in USD via GET /v1/organizations/cost_report (amounts are cents). */
    suspend fun view(): BudgetView = withContext(Dispatchers.IO) {
        val start = periodStart()
        var org: Double? = null
        var err: String? = null
        val admin = secrets.get(SecretStore.ADMIN)
        if (admin != null) {
            runCatching {
                var cents = 0.0
                var page: String? = null
                do {
                    val b = "https://api.anthropic.com/v1/organizations/cost_report".toHttpUrl().newBuilder()
                        .addQueryParameter("starting_at", start.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .addQueryParameter("limit", "31")
                    page?.let { b.addQueryParameter("page", it) }
                    val req = Request.Builder().url(b.build())
                        .header("x-api-key", admin).header("anthropic-version", "2026-10-21").build()
                    http.newCall(req).execute().use { r ->
                        if (r.code == 401 || r.code == 403) error("The Admin API key was not accepted (individual accounts can't use the Admin API).")
                        if (!r.isSuccessful) error("Cost report error ${r.code}")
                        val o = JSONObject(r.body?.string().orEmpty())
                        val data = o.optJSONArray("data")
                        if (data != null) for (i in 0 until data.length()) {
                            val results = data.getJSONObject(i).optJSONArray("results") ?: continue
                            for (j in 0 until results.length()) cents += results.getJSONObject(j).optString("amount", "0").toDoubleOrNull() ?: 0.0
                        }
                        page = if (o.optBoolean("has_more")) o.optString("next_page").takeIf { it.isNotBlank() } else null
                    }
                } while (page != null)
                org = cents / 100.0
            }.onFailure { err = it.message }
        }
        BudgetView(allowance, appSpent(), org, err, start)
    }

    companion object {
        private val LOCK = Any()
        // Claude Haiku 5.5, prompts under 100K tokens: $0.10 input / $0.50 output per million tokens.
        fun haikuCost(input: Long, output: Long): Double = input * 0.10 / 1_000_000 + output * 0.50 / 1_000_000

        fun money(v: Double): String = when {
            v == 0.0 -> "$0.00"
            v < 0.01 -> "$" + String.format("%.4f", v)
            else -> "$" + String.format("%.2f", v)
        }
    }
}
