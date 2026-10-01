package io.github.manawear.phone

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.HttpUsageSource
import io.github.manawear.shared.RELAY_PATH
import io.github.manawear.shared.encodePayload
import io.github.manawear.shared.parseInstant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.Instant

const val DATA_KEY_PAYLOAD = "payload"

/** A user with two machines runs a collector on each; the phone keeps up to this many. */
const val MAX_COLLECTORS = 4

data class Collector(val url: String, val token: String?)

data class PhoneConfig(val collectors: List<Collector> = emptyList()) {
    /** Adds [c], or replaces the one with the same URL. The oldest drops off past the cap. */
    fun with(c: Collector): PhoneConfig =
        PhoneConfig((collectors.filterNot { it.url == c.url } + c).takeLast(MAX_COLLECTORS))

    fun without(url: String): PhoneConfig = PhoneConfig(collectors.filterNot { it.url == url })
}

class PhoneConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun load(): PhoneConfig {
        val list = (0 until MAX_COLLECTORS).mapNotNull { i ->
            prefs.getString("url$i", null)?.takeIf { it.isNotBlank() }?.let { Collector(it, prefs.getString("token$i", null)) }
        }
        if (list.isNotEmpty()) return PhoneConfig(list)
        // Before multi-collector support the phone kept one URL under plain keys.
        val url = prefs.getString("url", null)?.takeIf { it.isNotBlank() } ?: return PhoneConfig()
        return PhoneConfig(listOf(Collector(url, prefs.getString("token", null))))
    }

    fun save(config: PhoneConfig) {
        val e = prefs.edit().remove("url").remove("token")
        for (i in 0 until MAX_COLLECTORS) {
            val c = config.collectors.getOrNull(i)
            e.putString("url$i", c?.url).putString("token$i", c?.token)
        }
        e.apply()
    }

    var lastWatchRequestAt: Long
        get() = prefs.getLong("lastWatchRequestAt", 0L)
        set(value) = prefs.edit().putLong("lastWatchRequestAt", value).apply()
}

/**
 * One relay answer. Every collector is fetched at once and the reading with the newest
 * observedAt wins, so the watch shows whichever machine ran Claude Code last. When none
 * answers, a rejected token is reported before anything else, because only the user can
 * fix it. Pure apart from the fetch it is given, so it is unit-tested.
 */
suspend fun relayAnswer(
    config: PhoneConfig,
    fetch: suspend (url: String, token: String?) -> FetchResult,
): FetchResult = coroutineScope {
    val targets = config.collectors.filter { it.url.isNotBlank() }
    if (targets.isEmpty()) return@coroutineScope FetchResult.NotConfigured
    val results = targets.map { c -> async { fetch(c.url, c.token) } }.awaitAll()
    results.filterIsInstance<FetchResult.Ok>()
        .maxByOrNull { parseInstant(it.payload.observedAt) ?: Instant.MIN }
        ?: results.firstOrNull { it == FetchResult.Unauthorized }
        ?: results.first()
}

/**
 * Fetches the collectors and, on success, publishes the payload to the DataItem the watch
 * reads on a cold start. The tokens are used here and never leave the phone.
 */
class Relay(context: Context) {
    private val app = context.applicationContext
    private val store = PhoneConfigStore(app)

    suspend fun answer(): FetchResult = answer(store.load())

    suspend fun answer(config: PhoneConfig): FetchResult {
        val result = relayAnswer(config) { url, token ->
            // Bounded well inside the watch's 15 s wait, so a slow collector comes back as an
            // error envelope rather than a relay timeout. The fetches run in parallel.
            HttpUsageSource(url, token, connectTimeoutMs = 4_000, readTimeoutMs = 6_000).fetch()
        }
        if (result is FetchResult.Ok) publish(result)
        return result
    }

    private suspend fun publish(result: FetchResult.Ok) {
        val req = PutDataMapRequest.create(RELAY_PATH).apply {
            dataMap.putByteArray(DATA_KEY_PAYLOAD, encodePayload(result.payload))
        }.asPutDataRequest().setUrgent()
        // No paired watch, or no Wearable API on this phone: the live answer still works.
        runCatching { Wearable.getDataClient(app).putDataItem(req).await() }
    }
}
