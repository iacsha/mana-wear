package io.github.manawear.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun decodesRealCollectorResponse() {
        val d = decodePayload(fixture("collector-teamclaude.json"))
        assertTrue(d is Decoded.Ok)
        val p = (d as Decoded.Ok).payload
        assertEquals("teamclaude", p.source)
        assertEquals(4.0, p.fiveHour!!.usedPercentage, 0.0)
        assertEquals(42.3, p.weekly!!.usedPercentage, 0.0)
    }

    @Test fun futureSchemaIsUnsupportedNotFigures() {
        val d = decodePayload("""{"schemaVersion":2,"fiveHour":{"usedPercentage":50}}""")
        assertEquals(Decoded.Unsupported(2), d)
    }

    @Test fun missingSchemaVersionIsUnsupported() {
        assertEquals(Decoded.Unsupported(null), decodePayload("""{"source":"x"}"""))
    }

    @Test fun garbageIsMalformed() {
        assertEquals(Decoded.Malformed, decodePayload("<html>"))
    }

    @Test fun nullWindowIsUnknownNotZero() {
        val d = decodePayload(
            """{"schemaVersion":1,"source":"none","observedAt":null,"stale":true,"fiveHour":null,"weekly":null}"""
        ) as Decoded.Ok
        assertNull(d.payload.fiveHour)
        assertEquals("--", formatPercent(d.payload.fiveHour))
    }
}
