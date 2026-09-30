package io.github.manawear.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlProblemTest {
    @Test fun emptyClearsTheSetting() = assertNull(urlProblem(""))

    @Test fun allowedUrlsSave() {
        assertNull(urlProblem("https://my-pc.tailnet.ts.net/v1/usage"))
        assertNull(urlProblem("http://100.101.102.103:7339/v1/usage"))
    }

    @Test fun publicHttpGetsThePolicyMessage() {
        assertEquals(HTTP_REFUSED_MESSAGE, urlProblem("http://my-pc.tailnet.ts.net/v1/usage"))
        assertEquals(HTTP_REFUSED_MESSAGE, urlProblem("HTTP://8.8.8.8/v1/usage"))
    }

    @Test fun otherInputAsksForAUrl() {
        assertEquals("Enter an https:// or http:// URL.", urlProblem("my-pc:7339"))
    }
}
