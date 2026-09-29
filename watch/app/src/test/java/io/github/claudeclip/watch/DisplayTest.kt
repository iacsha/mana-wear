package io.github.claudeclip.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DisplayTest {
    private val now = Instant.parse("2026-09-29T15:00:00Z")

    @Test fun countdownFormats() {
        assertEquals("1h 12m", formatCountdown(Instant.parse("2026-09-29T16:12:30Z"), now))
        assertEquals("12m", formatCountdown(Instant.parse("2026-09-29T15:12:00Z"), now))
        assertEquals("1d 13h", formatCountdown(Instant.parse("2026-10-01T04:00:00Z"), now))
        assertEquals("now", formatCountdown(now, now))
        assertEquals("now", formatCountdown(Instant.parse("2026-09-29T14:00:00Z"), now))
        assertNull(formatCountdown(null, now))
    }

    @Test fun ageUsesWatchClockNotPayloadAge() {
        assertEquals(90L, ageSeconds(Instant.parse("2026-09-29T14:58:30Z"), now))
        assertEquals(0L, ageSeconds(Instant.parse("2026-09-29T15:00:05Z"), now))
        assertEquals("1m", formatAge(90))
        assertEquals("45s", formatAge(45))
        assertEquals("2h", formatAge(7300))
    }

    private fun payload(observedAt: String?, stale: Boolean) =
        Payload(schemaVersion = 1, source = "teamclaude", observedAt = observedAt, stale = stale)

    @Test fun oldReadingIsStale() {
        assertFalse(isStale(payload("2026-09-29T14:59:00Z", false), now))
        assertTrue(isStale(payload("2026-09-29T14:30:00Z", false), now))
        assertTrue(isStale(payload(null, false), now))
    }

    @Test fun collectorStaleFlagWins() {
        assertTrue(isStale(payload("2026-09-29T14:59:59Z", true), now))
    }

    @Test fun percentFormats() {
        assertEquals("42%", formatPercent(Window(42.3)))
        assertEquals("2.7%", formatPercent(Window(2.7)))
        assertEquals("4%", formatPercent(Window(4.0)))
    }
}
