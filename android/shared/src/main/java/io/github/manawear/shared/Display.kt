package io.github.manawear.shared

import java.time.Duration
import java.time.Instant

// Pure display logic, kept free of Android types so it runs in JVM unit tests.

const val STALE_AFTER_SECONDS = 900L

fun parseInstant(text: String?): Instant? =
    text?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** `2d 4h`, `1h 12m`, `12m`, or `now`. Null when the reset time is unknown. */
fun formatCountdown(resetsAt: Instant?, now: Instant): String? {
    resetsAt ?: return null
    val minutes = Duration.between(now, resetsAt).toMinutes()
    if (minutes <= 0) return "now"
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

/**
 * Age of the reading by the watch's own clock. The payload's `ageSeconds` is only true at
 * the instant the collector answered, so it is ignored here. Clock skew can put
 * `observedAt` slightly in the future; that reads as zero, not negative.
 */
fun ageSeconds(observedAt: Instant?, now: Instant): Long? =
    observedAt?.let { maxOf(0L, Duration.between(it, now).seconds) }

fun formatAge(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    seconds < 86400 -> "${seconds / 3600}h"
    else -> "${seconds / 86400}d"
}

/** Stale when the collector says so, when the age is unknown, or when it is too old. */
fun isStale(payload: Payload, now: Instant, thresholdSeconds: Long = STALE_AFTER_SECONDS): Boolean {
    if (payload.stale) return true
    val age = ageSeconds(parseInstant(payload.observedAt), now) ?: return true
    return age > thresholdSeconds
}

/** `42%` with no decimals past 10, one decimal below it. `--` when the window is unknown. */
fun formatPercent(window: Window?): String {
    window ?: return "--"
    val p = window.usedPercentage
    return if (p < 10 && p != Math.floor(p)) "%.1f%%".format(p) else "${Math.round(p)}%"
}
