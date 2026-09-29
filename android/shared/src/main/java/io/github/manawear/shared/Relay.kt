package io.github.manawear.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The phone-to-watch relay protocol. The watch sends an empty request on RELAY_PATH with
// MessageClient.sendRequest; the phone fetches the collector and answers with one
// envelope. The token stays on the phone: only the payload and a status cross the link.

const val RELAY_PATH = "/mana/usage"
const val RELAY_CAPABILITY = "mana_relay"
const val ENVELOPE_VERSION = 1

@Serializable
private data class Envelope(
    val v: Int,
    val status: String,
    val payload: Payload? = null,
    val code: Int? = null,
)

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun encodeEnvelope(result: FetchResult): ByteArray {
    val e = when (result) {
        is FetchResult.Ok -> Envelope(ENVELOPE_VERSION, "ok", payload = result.payload)
        FetchResult.Unauthorized -> Envelope(ENVELOPE_VERSION, "unauthorized")
        is FetchResult.HttpError -> Envelope(ENVELOPE_VERSION, "http-error", code = result.code)
        is FetchResult.Unsupported -> Envelope(ENVELOPE_VERSION, "unsupported", code = result.schemaVersion)
        FetchResult.Malformed -> Envelope(ENVELOPE_VERSION, "malformed")
        FetchResult.NotConfigured -> Envelope(ENVELOPE_VERSION, "not-configured")
        // The collector was unreachable from the phone. The watch-local outcomes never
        // reach this function in practice; they map here rather than throwing.
        is FetchResult.Unreachable, FetchResult.NoPhone, FetchResult.NoPhoneApp ->
            Envelope(ENVELOPE_VERSION, "unreachable")
    }
    return json.encodeToString(Envelope.serializer(), e).toByteArray()
}

fun decodeEnvelope(bytes: ByteArray): FetchResult {
    val e = try {
        json.decodeFromString(Envelope.serializer(), bytes.decodeToString())
    } catch (_: Exception) {
        return FetchResult.Malformed
    }
    if (e.v != ENVELOPE_VERSION) return FetchResult.Unsupported(null)
    return when (e.status) {
        "ok" -> e.payload?.let { if (it.schemaVersion == SUPPORTED_SCHEMA) FetchResult.Ok(it) else FetchResult.Unsupported(it.schemaVersion) }
            ?: FetchResult.Malformed
        "unauthorized" -> FetchResult.Unauthorized
        "http-error" -> FetchResult.HttpError(e.code ?: 0)
        "unsupported" -> FetchResult.Unsupported(e.code)
        "malformed" -> FetchResult.Malformed
        "not-configured" -> FetchResult.NotConfigured
        "unreachable" -> FetchResult.Unreachable("collector")
        else -> FetchResult.Malformed
    }
}

/** The cached payload the phone writes to the DataItem at RELAY_PATH. */
fun encodePayload(payload: Payload): ByteArray =
    json.encodeToString(Payload.serializer(), payload).toByteArray()

fun decodeCachedPayload(bytes: ByteArray): Payload? =
    (decodePayload(bytes.decodeToString()) as? Decoded.Ok)?.payload
