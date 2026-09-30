package io.github.manawear.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection

sealed interface FetchResult {
    data class Ok(val payload: Payload) : FetchResult
    data object Unauthorized : FetchResult
    data class Unreachable(val reason: String) : FetchResult
    data class HttpError(val code: Int) : FetchResult
    data class Unsupported(val schemaVersion: Int?) : FetchResult
    data object Malformed : FetchResult

    // Relay outcomes. The phone reports NotConfigured; the watch decides the other two
    // locally and never sends them over the Data Layer.
    data object NotConfigured : FetchResult
    data object NoPhone : FetchResult
    data object NoPhoneApp : FetchResult
}

/**
 * Where readings come from. The UI only sees this interface: the watch uses the phone
 * relay by default and direct HTTP when a URL is set, and the phone uses direct HTTP.
 */
interface UsageSource {
    suspend fun fetch(): FetchResult

    /** The last reading this source kept, for a cold start before the first fetch. */
    suspend fun cached(): Payload? = null
}

class HttpUsageSource(
    private val url: String,
    private val token: String?,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 10_000,
) : UsageSource {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val uri = parseHttpUri(url) ?: return@withContext FetchResult.Unreachable("bad-url")
        // Checked before connecting, so a refused URL never sees the token.
        if (!cleartextAllowed(url)) return@withContext FetchResult.Unreachable("cleartext-refused")
        val conn = try {
            // The URL that was checked is the URL that is opened: same parse, no re-parse.
            uri.toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext FetchResult.Unreachable("bad-url")
        }
        try {
            // A redirect would carry the token to a host the policy never checked.
            conn.instanceFollowRedirects = false
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
