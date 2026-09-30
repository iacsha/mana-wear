package io.github.manawear.shared

import java.net.URI
import java.net.URLDecoder

data class Pairing(val url: String, val token: String)

const val PAIRING_MIN_TOKEN_LENGTH = 16

/**
 * Reads the code `mana-collector pair` prints:
 * `mana://pair?v=1&url=<percent-encoded URL>&token=<percent-encoded token>`.
 *
 * Returns null for anything else, including a URL [cleartextAllowed] refuses, a short token,
 * or a repeated parameter (two `url=` values would leave it unclear which one was shown).
 */
fun parsePairingUri(s: String): Pairing? {
    val uri = try {
        URI(s.trim())
    } catch (e: Exception) {
        return null
    }
    if (uri.scheme != "mana" || uri.host != "pair") return null
    if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return null
    val query = uri.rawQuery ?: return null

    val params = HashMap<String, String>()
    for (pair in query.split("&")) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        if (eq <= 0) return null
        val key = decode(pair.substring(0, eq)) ?: return null
        val value = decode(pair.substring(eq + 1)) ?: return null
        if (params.put(key, value) != null) return null
    }

    if (params["v"] != "1") return null
    val url = params["url"]?.trim()?.takeIf { it.isNotEmpty() && cleartextAllowed(it) } ?: return null
    val token = params["token"]?.trim() ?: return null
    if (token.length < PAIRING_MIN_TOKEN_LENGTH) return null
    return Pairing(url, token)
}

// The String overload: the Charset one needs API 33 and the apps start at 29.
private fun decode(s: String): String? = try {
    URLDecoder.decode(s, "UTF-8")
} catch (e: IllegalArgumentException) {
    null
}
