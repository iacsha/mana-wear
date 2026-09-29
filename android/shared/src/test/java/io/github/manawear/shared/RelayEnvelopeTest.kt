package io.github.manawear.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelayEnvelopeTest {
    private val payload = (decodePayload(
        javaClass.classLoader!!.getResource("collector-teamclaude.json")!!.readText()
    ) as Decoded.Ok).payload

    private fun roundTrip(r: FetchResult) = decodeEnvelope(encodeEnvelope(r))

    @Test fun everyPhoneOutcomeRoundTrips() {
        listOf(
            FetchResult.Ok(payload),
            FetchResult.Unauthorized,
            FetchResult.HttpError(502),
            FetchResult.Unsupported(2),
            FetchResult.Malformed,
            FetchResult.NotConfigured,
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test fun unreachableArrivesAsUnreachable() {
        assert(roundTrip(FetchResult.Unreachable("ConnectException")) is FetchResult.Unreachable)
    }

    @Test fun watchLocalOutcomesNeverClaimSuccess() {
        assert(roundTrip(FetchResult.NoPhone) is FetchResult.Unreachable)
        assert(roundTrip(FetchResult.NoPhoneApp) is FetchResult.Unreachable)
    }

    @Test fun futureEnvelopeIsUnsupported() {
        assertEquals(FetchResult.Unsupported(null), decodeEnvelope("""{"v":2,"status":"ok"}""".toByteArray()))
    }

    @Test fun garbageIsMalformed() {
        assertEquals(FetchResult.Malformed, decodeEnvelope("nope".toByteArray()))
        assertEquals(FetchResult.Malformed, decodeEnvelope("""{"v":1,"status":"ok"}""".toByteArray()))
    }

    @Test fun envelopeNeverCarriesAToken() {
        val text = encodeEnvelope(FetchResult.Ok(payload)).decodeToString()
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("authorization", ignoreCase = true))
    }

    @Test fun cachedPayloadRoundTrips() {
        assertEquals(payload, decodeCachedPayload(encodePayload(payload)))
    }
}
