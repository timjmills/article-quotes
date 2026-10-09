package com.tim.articlequotes.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How fresh the quotes are, from three clocks:
 *  - when this phone last downloaded the feed,
 *  - when the PC last published it (manifest "generated"),
 *  - the date of the newest article in it (manifest "newest", or the newest quote).
 * Any of them going quiet for a few days means something upstream has stopped.
 */
data class Staleness(
    val phoneSyncDays: Long?,
    val publishedDays: Long?,
    val newestDate: LocalDate?,
    val newestDays: Long?,
    val warn: Boolean,
    val message: String,
    val summary: String,
) {
    companion object {
        private val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        fun of(repo: FeedRepo, prefs: Prefs): Staleness? {
            val m = repo.manifest() ?: return null
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val phone = prefs.lastSync.takeIf { it > 0 }?.let {
                ChronoUnit.DAYS.between(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today)
            }
            val published = runCatching {
                ChronoUnit.DAYS.between(Instant.parse(m.getString("generated").replace("+00:00", "Z")).atZone(zone).toLocalDate(), today)
            }.getOrNull()
            val newest = runCatching { LocalDate.parse(m.getString("newest")) }.getOrNull()
                ?: repo.allQuotes().maxOfOrNull { it.date }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val newestDays = newest?.let { ChronoUnit.DAYS.between(it, today) }

            val message = when {
                phone != null && phone >= 3 ->
                    "This phone hasn't downloaded new quotes for $phone days. Open the app on Wi-Fi and tap Update now."
                newestDays != null && newestDays >= 5 ->
                    "No new articles since ${newest!!.format(fmt)}. The archiving task on your PC, or the PC itself, may have stopped."
                else -> ""
            }
            val summary = buildString {
                append(if (phone == null) "Not downloaded yet" else if (phone == 0L) "Updated today" else "Updated $phone day${if (phone == 1L) "" else "s"} ago")
                newest?.let { append(" · newest article ").append(it.format(fmt)) }
            }
            if (message.isEmpty()) prefs.staleNotified = false
            return Staleness(phone, published, newest, newestDays, message.isNotEmpty(), message, summary)
        }
    }
}
