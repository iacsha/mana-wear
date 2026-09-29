package io.github.manawear.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigTest {
    private val set = Config("http://a:7339/v1/usage", "secret-token-1234")

    @Test fun firstConfig() {
        assertEquals(set, applyConfig(Config(null, null), set.url, set.token))
    }

    @Test fun newUrlWithoutTokenDropsTheOldToken() {
        assertEquals(Config("http://evil/", null), applyConfig(set, "http://evil/", null))
    }

    @Test fun tokenOnlyRotatesToken() {
        assertEquals(set.copy(token = "new-token-56789012"), applyConfig(set, null, "new-token-56789012"))
    }

    @Test fun emptyIntentChangesNothing() {
        assertEquals(set, applyConfig(set, null, null))
        assertEquals(set, applyConfig(set, "  ", ""))
    }
}
