package io.github.manawear.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    private val token = "0123456789abcdef-token"
    private val url = "https://my-pc.tailnet.ts.net/v1/usage"
    private val encUrl = "https%3A%2F%2Fmy-pc.tailnet.ts.net%2Fv1%2Fusage"

    @Test fun parsesAValidCode() {
        assertEquals(Pairing(url, token), parsePairingUri("mana://pair?v=1&url=$encUrl&token=$token"))
    }

    @Test fun decodesPercentEncodedToken() {
        val p = parsePairingUri("mana://pair?v=1&url=$encUrl&token=abc%2Bdef%2F0123456789%3D")
        assertEquals("abc+def/0123456789=", p?.token)
    }

    @Test fun acceptsPrivateHttpAndParameterOrder() {
        val p = parsePairingUri("mana://pair?token=$token&url=http%3A%2F%2F100.64.1.2%3A7339%2Fv1%2Fusage&v=1")
        assertEquals(Pairing("http://100.64.1.2:7339/v1/usage", token), p)
    }

    @Test fun toleratesSurroundingWhitespace() {
        assertEquals(Pairing(url, token), parsePairingUri("  mana://pair?v=1&url=$encUrl&token=$token\n"))
    }

    @Test fun rejectsBadCodes() {
        for (s in listOf(
            "",
            "https://example.com",
            "mana://pair",
            "http://pair?v=1&url=$encUrl&token=$token",
            "mana://other?v=1&url=$encUrl&token=$token",
            "mana://pair/extra?v=1&url=$encUrl&token=$token",
            "mana://pair?v=2&url=$encUrl&token=$token",
            "mana://pair?url=$encUrl&token=$token",
            "mana://pair?v=1&token=$token",
            "mana://pair?v=1&url=&token=$token",
            "mana://pair?v=1&url=$encUrl",
            "mana://pair?v=1&url=$encUrl&token=short",
            "mana://pair?v=1&url=$encUrl&token=%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20",
            "mana://pair?v=1&url=http%3A%2F%2F8.8.8.8%2Fv1%2Fusage&token=$token",
            "mana://pair?v=1&url=http%3A%2F%2Fmy-pc.tailnet.ts.net%2Fv1%2Fusage&token=$token",
            "mana://pair?v=1&url=$encUrl&url=http%3A%2F%2F10.0.0.1%2F&token=$token",
            "mana://pair?v=1&url=$encUrl&token=$token&token=$token",
            "mana://pair?v=1&url=%ZZ&token=$token",
        )) assertNull(s, parsePairingUri(s))
    }

    @Test fun hostOfUrl() {
        assertEquals("my-pc.tailnet.ts.net", urlHost(url))
        assertEquals("::1", urlHost("http://[::1]:7339/"))
        assertNull(urlHost("not a url"))
    }
}
