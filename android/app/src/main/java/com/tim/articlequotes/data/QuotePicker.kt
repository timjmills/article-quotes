package com.tim.articlequotes.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Weighted random choice: newer articles and unseen quotes are favoured, and quotes
 * that are too long for a lock screen are excluded when a maximum is given.
 */
object QuotePicker {
    fun pick(pool: List<Quote>, seen: Set<String>, maxChars: Int?, rng: Random = Random.Default): Quote? {
        if (pool.isEmpty()) return null
        val today = LocalDate.now()
        val candidates = ArrayList<Pair<Quote, Double>>(pool.size)
        var total = 0.0
        for (q in pool) {
            if (maxChars != null && q.text.length > maxChars) continue
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(q.date), today) }.getOrDefault(400L)
            val recency = when {
                days <= 14 -> 5.0
                days <= 45 -> 3.0
                days <= 120 -> 2.0
                days <= 365 -> 1.3
                else -> 1.0
            }
            val fresh = if (q.id in seen) 0.12 else 1.0
            // Slight preference for mid-length quotes: they read best on a phone.
            val len = q.text.length
            val fit = when {
                len < 70 -> 0.8
                len <= 220 -> 1.0
                len <= 320 -> 0.8
                else -> 0.6
            }
            val w = recency * fresh * fit
            candidates.add(q to w); total += w
        }
        if (candidates.isEmpty()) return null
        var r = rng.nextDouble() * total
        for ((q, w) in candidates) {
            r -= w
            if (r <= 0) return q
        }
        return candidates.last().first
    }
}
