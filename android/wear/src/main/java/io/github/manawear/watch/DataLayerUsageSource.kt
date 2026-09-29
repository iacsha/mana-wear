package io.github.manawear.watch

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Payload
import io.github.manawear.shared.RELAY_CAPABILITY
import io.github.manawear.shared.RELAY_PATH
import io.github.manawear.shared.decodeCachedPayload
import io.github.manawear.shared.decodeEnvelope
import io.github.manawear.shared.UsageSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

const val RELAY_TIMEOUT_MS = 15_000L
const val DATA_KEY_PAYLOAD = "payload"

/**
 * Asks the phone for a reading over the Wear Data Layer. The phone holds the collector URL
 * and token and does the HTTP fetch; this side only ever sees the payload and a status.
 */
class DataLayerUsageSource(context: Context) : UsageSource {
    private val app = context.applicationContext

    override suspend fun fetch(): FetchResult = try {
        withTimeout(RELAY_TIMEOUT_MS) {
            val relays = Wearable.getCapabilityClient(app)
                .getCapability(RELAY_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            if (relays.isNotEmpty()) {
                val node = relays.firstOrNull { it.isNearby } ?: relays.first()
                return@withTimeout ask(node.id)
            }
            // Capabilities can lag for a while after an install, so try a connected phone
            // directly before telling "no phone" apart from "phone without Mana".
            val connected = Wearable.getNodeClient(app).connectedNodes.await()
            val node = connected.firstOrNull { it.isNearby } ?: connected.firstOrNull()
                ?: return@withTimeout FetchResult.NoPhone
            try {
                ask(node.id)
            } catch (e: TimeoutCancellationException) {
                throw e
            } catch (_: Exception) {
                FetchResult.NoPhoneApp
            }
        }
    } catch (_: TimeoutCancellationException) {
        FetchResult.Unreachable("relay-timeout")
    } catch (_: Exception) {
        FetchResult.Unreachable("relay")
    }

    private suspend fun ask(nodeId: String): FetchResult =
        decodeEnvelope(Wearable.getMessageClient(app).sendRequest(nodeId, RELAY_PATH, ByteArray(0)).await())

    override suspend fun cached(): Payload? = try {
        val items = Wearable.getDataClient(app).dataItems.await()
        try {
            items.firstOrNull { it.uri.path == RELAY_PATH }
                ?.let { DataMapItem.fromDataItem(it).dataMap.getByteArray(DATA_KEY_PAYLOAD) }
                ?.let(::decodeCachedPayload)
        } finally {
            items.release()
        }
    } catch (_: Exception) {
        null
    }
}
