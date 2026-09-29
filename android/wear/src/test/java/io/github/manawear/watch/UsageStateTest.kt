package io.github.manawear.watch

import io.github.manawear.shared.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageStateTest {
    private val good = Payload(1, "teamclaude", "2026-09-29T15:00:00Z", false, Window(40.0), Window(10.0))
    private val base = UsageState(lastGood = good)

    @Test fun unreachableKeepsLastReading() {
        val s = base.after(FetchResult.Unreachable("ConnectException"))
        assertEquals(good, s.lastGood)
        assertEquals(Problem.UNREACHABLE, s.problem)
    }

    @Test fun rejectedTokenIsDistinct() {
        assertEquals(Problem.TOKEN_REJECTED, base.after(FetchResult.Unauthorized).problem)
    }

    @Test fun unsupportedDropsTheReading() {
        assertNull(base.after(FetchResult.Unsupported(2)).lastGood)
    }

    @Test fun successClearsProblem() {
        val s = base.copy(problem = Problem.UNREACHABLE).after(FetchResult.Ok(good))
        assertNull(s.problem)
    }
}
