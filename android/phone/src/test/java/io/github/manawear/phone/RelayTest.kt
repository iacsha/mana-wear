package io.github.manawear.phone

import io.github.manawear.shared.FetchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelayTest {
    @Test fun noUrlIsNotConfiguredAndNeverFetches() = runTest {
        var fetched = false
        val r = relayAnswer(PhoneConfig(null, "t")) { _, _ -> fetched = true; FetchResult.Malformed }
        assertEquals(FetchResult.NotConfigured, r)
        assertFalse(fetched)
    }

    @Test fun blankUrlIsNotConfigured() = runTest {
        assertEquals(FetchResult.NotConfigured, relayAnswer(PhoneConfig("  ", null)) { _, _ -> FetchResult.Malformed })
    }

    @Test fun passesUrlAndTokenToFetch() = runTest {
        var seen: Pair<String, String?>? = null
        relayAnswer(PhoneConfig("http://c/v1/usage", "tok")) { u, t -> seen = u to t; FetchResult.Unauthorized }
        assertEquals("http://c/v1/usage" to "tok", seen)
    }
}
