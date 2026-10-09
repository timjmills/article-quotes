package com.tim.articlequotes.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Everything the picker knows about what Tim likes, read once per pick. */
data class Taste(
    val seen: Set<String>,
    val read: Set<String>,
    val authorScores: Map<String, Int>,
    val articleScores: Map<String, Int>,
    val muted: Set<String>,
    val categoryWeights: Map<String, Int>,
    val timeOfDay: Boolean,
) {
    companion object {
        fun from(p: Prefs) = Taste(
            seen = p.seenIds, read = p.readArticles, authorScores = p.authorScores, articleScores = p.articleScores,
            muted = p.mutedAuthors, categoryWeights = Categories.ALL.associateWith { p.categoryWeight(it) },
            timeOfDay = p.timeOfDayThemes,
        )
    }
}

/**
 * Weighted random choice. Newer articles, unseen quotes, liked authors and articles, and the
 * categories you turned up are favoured; muted authors never appear; articles you've already
 * read come up less. Quotes too long for the lock screen are skipped when a maximum is given.
 */
object QuotePicker {
    fun pick(pool: List<Quote>, taste: Taste, maxChars: Int?, rng: Random = Random.Default, now: LocalTime = LocalTime.now()): Quote? {
        if (pool.isEmpty()) return null
        val today = LocalDate.now()
        val candidates = ArrayList<Pair<Quote, Double>>(pool.size)
        var total = 0.0
        for (q in pool) {
            if (maxChars != null && q.text.length > maxChars) continue
            if (q.author in taste.muted) continue
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(q.date), today) }.getOrDefault(400L)
            val recency = when {
                days <= 14 -> 5.0
                days <= 45 -> 3.0
                days <= 120 -> 2.0
                days <= 365 -> 1.3
                else -> 1.0
            }
            val fresh = if (q.id in taste.seen) 0.12 else 1.0
            val len = q.text.length
            val fit = when {
                len < 70 -> 0.8
                len <= 220 -> 1.0
                len <= 320 -> 0.8
                else -> 0.6
            }
            val cat = when (taste.categoryWeights[q.category] ?: 1) { 0 -> 0.35; 2 -> 2.2; else -> 1.0 }
            val author = Math.pow(1.6, (taste.authorScores[q.author] ?: 0).toDouble())
            val article = Math.pow(2.0, (taste.articleScores[q.articleId] ?: 0).toDouble())
            val read = if (q.articleId in taste.read) 0.5 else 1.0
            val tod = if (taste.timeOfDay) timeOfDayBoost(q.category, now) else 1.0
            val w = recency * fresh * fit * cat * author * article * read * tod
            if (w <= 0.0) continue
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

    /** Mornings lean to work (leadership, teaching); evenings lean to home (family). */
    fun timeOfDayBoost(category: String, now: LocalTime): Double {
        val h = now.hour
        val morning = h in 5..11
        val evening = h >= 17 || h < 2
        return when {
            morning && (category == "Leadership" || category.startsWith("Education")) -> 1.6
            evening && category == "Family" -> 2.2
            evening && category == "Classical Education" -> 1.3
            else -> 1.0
        }
    }
}
