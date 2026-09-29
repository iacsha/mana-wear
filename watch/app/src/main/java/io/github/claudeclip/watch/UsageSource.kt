package io.github.claudeclip.watch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface FetchResult {
    data class Ok(val payload: Payload) : FetchResult
    data object Unauthorized : FetchResult
    data class Unreachable(val reason: String) : FetchResult
    data class HttpError(val code: Int) : FetchResult
    data class Unsupported(val schemaVersion: Int?) : FetchResult
    data object Malformed : FetchResult
}

/**
 * Where readings come from. The UI only sees this interface, so the Phase 5 phone relay
 * over the Data Layer drops in beside direct HTTP without touching screen code.
 */
fun interface UsageSource {
    suspend fun fetch(): FetchResult
}

class HttpUsageSource(
    private val url: String,
    private val token: String?,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 10_000,
) : UsageSource {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext FetchResult.Unreachable("bad-url")
        }
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")

            when (val code = conn.responseCode) {
                401, 403 -> FetchResult.Unauthorized
                in 200..299 -> when (val d = decodePayload(conn.inputStream.bufferedReader().use { it.readText() })) {
                    is Decoded.Ok -> FetchResult.Ok(d.payload)
                    is Decoded.Unsupported -> FetchResult.Unsupported(d.schemaVersion)
                    Decoded.Malformed -> FetchResult.Malformed
                }
                else -> FetchResult.HttpError(code)
            }
        } catch (e: IOException) {
            FetchResult.Unreachable(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }
}
