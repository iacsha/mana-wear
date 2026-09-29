package io.github.manawear.phone

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.HttpUsageSource
import io.github.manawear.shared.RELAY_PATH
import io.github.manawear.shared.encodePayload
import kotlinx.coroutines.tasks.await

const val DATA_KEY_PAYLOAD = "payload"

data class PhoneConfig(val url: String?, val token: String?)

class PhoneConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun load() = PhoneConfig(prefs.getString("url", null), prefs.getString("token", null))

    fun save(config: PhoneConfig) {
        prefs.edit().putString("url", config.url).putString("token", config.token).apply()
    }

    var lastWatchRequestAt: Long
        get() = prefs.getLong("lastWatchRequestAt", 0L)
        set(value) = prefs.edit().putLong("lastWatchRequestAt", value).apply()
}

/** One relay answer. Pure apart from the fetch it is given, so it is unit-tested. */
suspend fun relayAnswer(
    config: PhoneConfig,
    fetch: suspend (url: String, token: String?) -> FetchResult,
): FetchResult {
    val url = config.url?.takeIf { it.isNotBlank() } ?: return FetchResult.NotConfigured
    return fetch(url, config.token)
}

/**
 * Fetches the collector and, on success, publishes the payload to the DataItem the watch
 * reads on a cold start. The token is used here and never leaves the phone.
 */
class Relay(context: Context) {
    private val app = context.applicationContext
    private val store = PhoneConfigStore(app)

    suspend fun answer(): FetchResult {
        val result = relayAnswer(store.load()) { url, token -> // Bounded well inside the watch's 15 s wait, so a slow collector comes back as an
            // error envelope rather than a relay timeout.
            HttpUsageSource(url, token, connectTimeoutMs = 4_000, readTimeoutMs = 6_000).fetch() }
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
