package io.github.manawear.phone

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.RELAY_PATH
import io.github.manawear.shared.encodeEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Process-wide, so an answer in flight survives the service being unbound.
private val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class RelayService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != RELAY_PATH) return null
        val reply = TaskCompletionSource<ByteArray>()
        val relay = Relay(this)
        PhoneConfigStore(this).lastWatchRequestAt = System.currentTimeMillis()
        relayScope.launch {
            // Always complete the reply, or the watch waits out its timeout and blames the link.
            val result = try {
                relay.answer()
            } catch (e: Exception) {
                FetchResult.Unreachable("phone-error")
            }
            reply.trySetResult(encodeEnvelope(result))
        }
        return reply.task
    }
}
