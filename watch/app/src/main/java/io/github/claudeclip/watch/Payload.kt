package io.github.claudeclip.watch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The collector document, schema/payload.schema.json in the repo root. Only the fields
// the watch shows are modelled; anything else is ignored so an additive collector
// change never breaks an installed watch.

const val SUPPORTED_SCHEMA = 1

@Serializable
data class Window(
    val usedPercentage: Double,
    val resetsAt: String? = null,
)

@Serializable
data class Payload(
    val schemaVersion: Int,
    val source: String,
    val observedAt: String? = null,
    val stale: Boolean,
    val fiveHour: Window? = null,
    val weekly: Window? = null,
    val errors: List<String> = emptyList(),
)

sealed interface Decoded {
    data class Ok(val payload: Payload) : Decoded
    data class Unsupported(val schemaVersion: Int?) : Decoded
    data object Malformed : Decoded
}

private val json = Json { ignoreUnknownKeys = true }

fun decodePayload(text: String): Decoded {
    // Read the version first, so a future schema is reported as such rather than as a
    // decode error or, worse, as figures read with the wrong meaning.
    val version = try {
        json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.int
    } catch (_: Exception) {
        return Decoded.Malformed
    }
    if (version != SUPPORTED_SCHEMA) return Decoded.Unsupported(version)
    return try {
        Decoded.Ok(json.decodeFromString(Payload.serializer(), text))
    } catch (_: Exception) {
        Decoded.Malformed
    }
}
