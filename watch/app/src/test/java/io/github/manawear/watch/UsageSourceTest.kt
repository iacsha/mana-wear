package io.github.manawear.watch

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class UsageSourceTest {
    private lateinit var server: HttpServer
    private var seenAuth: String? = null
    private val body = javaClass.classLoader!!.getResource("collector-teamclaude.json")!!.readBytes()
    private val url get() = "http://127.0.0.1:${server.address.port}/v1/usage"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/usage") { ex ->
            seenAuth = ex.requestHeaders.getFirst("Authorization")
            if (seenAuth != "Bearer good-token-123456") {
                ex.sendResponseHeaders(401, -1)
            } else {
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    @Test fun sendsBearerAndDecodes() = runTest {
        val r = HttpUsageSource(url, "good-token-123456").fetch()
        assertEquals("Bearer good-token-123456", seenAuth)
        assertTrue(r is FetchResult.Ok)
    }

    @Test fun wrongTokenIsTokenRejected() = runTest {
        assertEquals(FetchResult.Unauthorized, HttpUsageSource(url, "wrong").fetch())
    }

    @Test fun noTokenSendsNoHeader() = runTest {
        HttpUsageSource(url, null).fetch()
        assertEquals(null, seenAuth)
    }

    @Test fun closedPortIsUnreachable() = runTest {
        val port = ServerSocket(0).use { it.localPort }
        val r = HttpUsageSource("http://127.0.0.1:$port/v1/usage", "t", connectTimeoutMs = 1000).fetch()
        assertTrue(r is FetchResult.Unreachable)
    }

    @Test fun defaultTimeoutsAreBounded() {
        val f = HttpUsageSource::class.java.getDeclaredField("connectTimeoutMs").apply { isAccessible = true }
        val g = HttpUsageSource::class.java.getDeclaredField("readTimeoutMs").apply { isAccessible = true }
        val s = HttpUsageSource(url, null)
        assertTrue((f.get(s) as Int) in 1..10_000)
        assertTrue((g.get(s) as Int) in 1..10_000)
    }
}
