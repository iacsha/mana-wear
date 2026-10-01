package io.github.manawear.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectorLabelTest {
    @Test fun portIsShownWhenPresent() {
        assertEquals("100.64.0.10:7339", collectorLabel("http://100.64.0.10:7339/v1/usage"))
        assertEquals("100.64.0.10:7340", collectorLabel("http://100.64.0.10:7340/v1/usage"))
    }

    @Test fun noPortShowsHostOnly() {
        assertEquals("my-pc.tailnet.ts.net", collectorLabel("https://my-pc.tailnet.ts.net/v1/usage"))
    }

    @Test fun unparseableFallsBackToTheUrl() {
        assertEquals("not a url", collectorLabel("not a url"))
    }
}
