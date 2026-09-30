package io.github.manawear.shared

import java.net.URI

/**
 * Whether the apps may send the token to [url]. https is always allowed. Plain http is
 * allowed only to a private, loopback, or Tailscale address, where the hop is either local
 * or already encrypted by WireGuard; anywhere else the token would cross the internet in
 * the clear. Android's network security config cannot express address ranges, so this is
 * the real cleartext policy for both apps.
 *
 * Literals only: no DNS lookup, so a hostname that happens to resolve to a private address
 * is still refused. `localhost` and `*.local` (mDNS) are the only names allowed.
 */
fun cleartextAllowed(url: String): Boolean {
    val uri = parseHttpUri(url) ?: return false
    if (uri.scheme.equals("https", ignoreCase = true)) return true
    return privateHost(uri.host)
}

/** The host of [url] without IPv6 brackets, for showing the user where a pairing points. */
fun urlHost(url: String): String? = parseHttpUri(url)?.host?.removeSurrounding("[", "]")

/** [url] as an absolute http or https URI with a host, or null. */
internal fun parseHttpUri(url: String): URI? {
    val uri = try {
        URI(url)
    } catch (e: Exception) {
        return null
    }
    val scheme = uri.scheme ?: return null
    if (!scheme.equals("http", true) && !scheme.equals("https", true)) return null
    if (uri.host.isNullOrEmpty()) return null
    return uri
}

private fun privateHost(rawHost: String): Boolean {
    val host = rawHost.lowercase()
    if (host.startsWith("[")) {
        val g = parseIpv6(host.removeSurrounding("[", "]")) ?: return false
        return when {
            g.take(7).all { it == 0 } && g[7] == 1 -> true // ::1
            g[0] shr 8 == 0xfd -> true // fd00::/8 ULA, which holds Tailscale's fd7a:115c:a1e0::/48
            g.take(5).all { it == 0 } && g[5] == 0xffff -> // ::ffff:a.b.c.d, IPv4-mapped
                privateIpv4(g[6] shr 8, g[6] and 0xff)
            else -> false
        }
    }
    parseIpv4(host)?.let { return privateIpv4(it[0], it[1]) }
    return host == "localhost" || (host.endsWith(".local") && host.length > ".local".length)
}

// 10/8, 172.16/12, 192.168/16, 100.64/10 (Tailscale CGNAT), 127/8.
private fun privateIpv4(a: Int, b: Int): Boolean =
    a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127) || a == 127

private val IPV4 = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""")

/** Four dotted decimal octets. Leading zeros are refused: some parsers read them as octal. */
private fun parseIpv4(s: String): IntArray? {
    val m = IPV4.matchEntire(s) ?: return null
    val parts = m.groupValues.drop(1)
    if (parts.any { it.length > 1 && it.startsWith("0") }) return null
    return parts.map { it.toInt() }.takeIf { o -> o.all { it <= 255 } }?.toIntArray()
}

private val HEX_GROUP = Regex("[0-9a-f]{1,4}")

/**
 * The eight 16-bit groups of an IPv6 literal, with `::` expanded and a trailing dotted IPv4
 * part folded into the last two groups. Parsed by hand because InetAddress may fall back to
 * a DNS lookup on Android when a literal does not parse. Zone ids are refused.
 */
private fun parseIpv6(s: String): IntArray? {
    if (s.isEmpty() || '%' in s) return null
    val halves = s.split("::")
    if (halves.size > 2) return null
    val parsed = halves.mapIndexed { h, half ->
        if (half.isEmpty()) return@mapIndexed emptyList()
        val parts = half.split(":")
        val values = ArrayList<Int>()
        for ((i, part) in parts.withIndex()) {
            // Dotted IPv4 is only valid as the very last part of the address.
            if ('.' in part && h == halves.lastIndex && i == parts.lastIndex) {
                val v4 = parseIpv4(part) ?: return null
                values += (v4[0] shl 8) or v4[1]
                values += (v4[2] shl 8) or v4[3]
            } else {
                if (!HEX_GROUP.matches(part)) return null
                values += part.toInt(16)
            }
        }
        values
    }
    if (parsed.size == 1) return parsed[0].takeIf { it.size == 8 }?.toIntArray()
    val (left, right) = parsed
    if (left.size + right.size > 7) return null
    return (left + List(8 - left.size - right.size) { 0 } + right).toIntArray()
}
