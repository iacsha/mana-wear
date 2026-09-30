package io.github.manawear.shared

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class UrlPolicyTest {
    @Test fun httpsIsAlwaysAllowed() {
        for (u in listOf(
            "https://my-pc.tailnet.ts.net/v1/usage",
            "https://8.8.8.8/v1/usage",
            "HTTPS://example.com:8443/v1/usage",
        )) assertTrue(u, cleartextAllowed(u))
    }

    @Test fun httpToPrivateAddressesIsAllowed() {
        for (u in listOf(
            "http://10.0.0.1:7339/v1/usage",
            "http://10.255.255.255/",
            "http://172.16.0.1/",
            "http://172.31.255.254/",
            "http://192.168.1.10:7339/v1/usage",
            "http://100.64.0.1/",
            "http://100.127.255.255/",
            "http://127.0.0.1:7339/v1/usage",
            "http://localhost:7339/v1/usage",
            "http://LOCALHOST/",
            "http://my-pc.local:7339/v1/usage",
            "http://[::1]:7339/v1/usage",
            "http://[fd7a:115c:a1e0::1]:7339/v1/usage",
            "http://[fd00::1]/",
            "HTTP://10.1.2.3/",
        )) assertTrue(u, cleartextAllowed(u))
    }

    @Test fun httpToPublicOrNamedHostsIsRefused() {
        for (u in listOf(
            "http://8.8.8.8/v1/usage",
            "http://172.15.255.255/",
            "http://172.32.0.1/",
            "http://100.63.255.255/",
            "http://100.128.0.1/",
            "http://192.169.0.1/",
            "http://11.0.0.1/",
            "http://example.com/v1/usage",
            "http://my-pc.tailnet.ts.net/v1/usage",
            "http://localhost.evil.com/",
            "http://10.0.0.1.evil.com/",
            "http://10.0.0.1@evil.com/",
            "http://evil.com#@10.0.0.1/",
            "http://[2001:4860:4860::8888]/",
            "http://[fe80::1]/",
            "http://[fc00::1]/",
            "http://[::ffff:8.8.8.8]/",
            "http://10.0.0/",
            "http://10.0.0.256/",
            "http://0x0a.0.0.1/",
            "http://010.0.0.1/",
        )) assertFalse(u, cleartextAllowed(u))
    }

    @Test fun unparsableOrOtherSchemesAreRefused() {
        for (u in listOf(
            "",
            "not a url",
            "10.0.0.1:7339/v1/usage",
            "ftp://10.0.0.1/",
            "file:///etc/hosts",
            "http://",
            "http:///v1/usage",
            "http://10.0.0.1\\@evil.com/",
        )) assertFalse(u, cleartextAllowed(u))
    }

    @Test fun refusedUrlIsNotFetched() = runTest {
        // Something listens on the port but never answers. A connect attempt would hang to
        // the read timeout; the guard must return before it.
        ServerSocket(0).use { s ->
            val r = HttpUsageSource("http://8.8.8.8:${s.localPort}/v1/usage", "t", 200, 200).fetch()
            assertEquals(FetchResult.Unreachable("cleartext-refused"), r)
        }
    }

    @Test fun allowedUrlIsStillFetched() = runTest {
        val port = ServerSocket(0).use { it.localPort }
        val r = HttpUsageSource("http://127.0.0.1:$port/v1/usage", "t", connectTimeoutMs = 1000).fetch()
        assertTrue(r is FetchResult.Unreachable && r.reason != "cleartext-refused")
    }
}
