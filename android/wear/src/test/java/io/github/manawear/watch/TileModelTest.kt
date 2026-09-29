package io.github.manawear.watch

import io.github.manawear.shared.Payload
import io.github.manawear.shared.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TileModelTest {
    private val now = Instant.parse("2026-09-29T16:50:00Z")
    private val ny = ZoneId.of("America/New_York")
    private val payload = Payload(
        schemaVersion = 1, source = "teamclaude", observedAt = "2026-09-29T16:49:30Z", stale = false,
        fiveHour = Window(45.0, "2026-09-29T18:10:00Z"),
        weekly = Window(47.7, "2026-10-01T03:59:59Z"),
    )

    @Test fun showsClockTimesNotCountdowns() {
        val m = tileModel(payload, now, ny, is24Hour = false, locale = Locale.US)
        assertEquals("45%", m.fiveHourPercent)
        assertEquals("resets 2:10 PM", m.fiveHourReset)
        assertEquals("week 48% · Wed 11 PM", m.weeklyLine)
        assertFalse(m.stale)
    }

    @Test fun twentyFourHourClock() {
        assertEquals("resets 14:10", tileModel(payload, now, ny, is24Hour = true, locale = Locale.US).fiveHourReset)
    }

    @Test fun ringsShowRemainingMana() {
        val m = tileModel(payload, now, ny, is24Hour = false, locale = Locale.US)
        assertEquals(0.55f, m.fiveHourRemaining, 0.001f)
        assertEquals(0.523f, m.weeklyRemaining, 0.001f)
    }

    @Test fun unknownWindowIsEmptyRingNotFull() {
        val m = tileModel(payload.copy(fiveHour = null), now, ny, is24Hour = false, locale = Locale.US)
        assertEquals("--", m.fiveHourPercent)
        assertEquals(0f, m.fiveHourRemaining)
    }

    @Test fun oldReadingIsStale() {
        assertTrue(tileModel(payload.copy(observedAt = "2026-09-29T16:00:00Z"), now, ny, false, Locale.US).stale)
    }
}
