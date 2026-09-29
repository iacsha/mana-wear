package io.github.manawear.watch

import io.github.manawear.shared.Payload
import io.github.manawear.shared.formatPercent
import io.github.manawear.shared.isStale
import io.github.manawear.shared.parseInstant
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the tile shows, as plain values. Built without Android types so it is unit-tested. */
data class TileModel(
    val fiveHourPercent: String,
    val fiveHourReset: String,
    val weeklyLine: String,
    /** 0..1 of the five-hour window still unused, for the ring. */
    val fiveHourRemaining: Float,
    val weeklyRemaining: Float,
    val stale: Boolean,
)

/**
 * The tile is only redrawn every few minutes, so it shows clock times ("resets 2:10 PM"),
 * which stay true, rather than countdowns, which would drift.
 */
fun tileModel(p: Payload, now: Instant, zone: ZoneId, is24Hour: Boolean, locale: Locale = Locale.getDefault()): TileModel {
    val five = p.fiveHour
    val week = p.weekly
    return TileModel(
        fiveHourPercent = formatPercent(five),
        fiveHourReset = resetClock(parseInstant(five?.resetsAt), now, zone, is24Hour, locale)?.let { "resets $it" } ?: "",
        weeklyLine = "week ${formatPercent(week)}" +
            (resetClock(parseInstant(week?.resetsAt), now, zone, is24Hour, locale)?.let { " · $it" } ?: ""),
        fiveHourRemaining = remaining(five?.usedPercentage),
        weeklyRemaining = remaining(week?.usedPercentage),
        stale = isStale(p, now),
    )
}

private fun remaining(used: Double?): Float =
    used?.let { (1 - it / 100).toFloat().coerceIn(0f, 1f) } ?: 0f

/** "2:10 PM" when the reset is within a day, "Wed 11 PM" beyond that. */
fun resetClock(at: Instant?, now: Instant, zone: ZoneId, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String? {
    at ?: return null
    if (!at.isAfter(now)) return "now"
    val soon = Duration.between(now, at) < Duration.ofHours(24)
    val pattern = when {
        soon && is24Hour -> "H:mm"
        soon -> "h:mm a"
        is24Hour -> "EEE H:mm"
        else -> "EEE h a"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(at)
}
