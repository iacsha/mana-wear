package io.github.manawear.phone

import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Payload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelayTest {
    private fun ok(observedAt: String?) =
        FetchResult.Ok(Payload(schemaVersion = 1, source = "statusline-tap", observedAt = observedAt, stale = false))

    private fun config(vararg urls: String) = PhoneConfig(urls.map { Collector(it, "t-$it") })

    @Test fun noCollectorIsNotConfiguredAndNeverFetches() = runTest {
        var fetched = false
        val r = relayAnswer(PhoneConfig()) { _, _ -> fetched = true; FetchResult.Malformed }
        assertEquals(FetchResult.NotConfigured, r)
        assertFalse(fetched)
    }

    @Test fun blankUrlIsNotConfigured() = runTest {
        assertEquals(FetchResult.NotConfigured, relayAnswer(PhoneConfig(listOf(Collector("  ", null)))) { _, _ -> FetchResult.Malformed })
    }

    @Test fun passesEachUrlWithItsOwnToken() = runTest {
        val seen = mutableSetOf<Pair<String, String?>>()
        relayAnswer(config("http://a/v1/usage", "http://b/v1/usage")) { u, t -> synchronized(seen) { seen += u to t }; FetchResult.Unauthorized }
        assertEquals(setOf("http://a/v1/usage" to "t-http://a/v1/usage", "http://b/v1/usage" to "t-http://b/v1/usage"), seen)
    }

    @Test fun newestObservedAtWins() = runTest {
        val readings = mapOf(
            "http://a" to ok("2026-10-01T10:00:00Z"),
            "http://b" to ok("2026-10-01T12:00:00Z"),
            "http://c" to ok("2026-10-01T11:00:00Z"),
        )
        val r = relayAnswer(config("http://a", "http://b", "http://c")) { u, _ -> readings.getValue(u) }
        assertEquals(readings.getValue("http://b"), r)
    }

    @Test fun aReadingBeatsFailures() = runTest {
        val good = ok("2026-10-01T10:00:00Z")
        val r = relayAnswer(config("http://a", "http://b")) { u, _ ->
            if (u == "http://a") FetchResult.Unreachable("ConnectException") else good
        }
        assertEquals(good, r)
    }

    @Test fun readingWithoutObservedAtLosesToOneWithIt() = runTest {
        val dated = ok("2026-10-01T10:00:00Z")
        val r = relayAnswer(config("http://a", "http://b")) { u, _ -> if (u == "http://a") ok(null) else dated }
        assertEquals(dated, r)
    }

    @Test fun rejectedTokenIsReportedBeforeUnreachable() = runTest {
        val r = relayAnswer(config("http://a", "http://b")) { u, _ ->
            if (u == "http://a") FetchResult.Unreachable("ConnectException") else FetchResult.Unauthorized
        }
        assertEquals(FetchResult.Unauthorized, r)
    }

    @Test fun allUnreachableReportsTheFirst() = runTest {
        val r = relayAnswer(config("http://a", "http://b")) { u, _ -> FetchResult.Unreachable(u) }
        assertEquals(FetchResult.Unreachable("http://a"), r)
    }

    @Test fun withReplacesSameUrlAndCapsAtFour() {
        var c = PhoneConfig()
        for (i in 1..5) c = c.with(Collector("http://h$i", "t$i"))
        assertEquals(listOf("http://h2", "http://h3", "http://h4", "http://h5"), c.collectors.map { it.url })
        c = c.with(Collector("http://h3", "new"))
        assertEquals(listOf("http://h2", "http://h4", "http://h5", "http://h3"), c.collectors.map { it.url })
        assertEquals("new", c.collectors.last().token)
    }

    @Test fun withoutRemovesByUrl() {
        val c = config("http://a", "http://b").without("http://a")
        assertEquals(listOf("http://b"), c.collectors.map { it.url })
    }
}
