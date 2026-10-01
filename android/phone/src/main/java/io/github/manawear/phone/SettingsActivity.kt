package io.github.manawear.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Pairing
import io.github.manawear.shared.cleartextAllowed
import io.github.manawear.shared.formatCountdown
import io.github.manawear.shared.formatPercent
import io.github.manawear.shared.parseInstant
import io.github.manawear.shared.parsePairingUri
import io.github.manawear.shared.urlHost
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date

const val HTTP_REFUSED_MESSAGE =
    "Plain http is only allowed to private or Tailscale addresses. Use https (tailscale serve) for anything else."

/**
 * Where the collector URL and token are entered, by hand, by scanning the code
 * `mana-collector pair` prints, or from a `mana://pair` link. The watch never sees the token.
 *
 * singleTop, so a pairing link that arrives while this is open lands in onNewIntent.
 */
class SettingsActivity : ComponentActivity() {
    private lateinit var store: PhoneConfigStore
    private lateinit var relay: Relay

    private var url by mutableStateOf("")
    private var token by mutableStateOf("")
    private var result by mutableStateOf("")
    private var collectors by mutableStateOf<List<Collector>>(emptyList())

    // A pairing from a link, waiting for the user to confirm it.
    private var pendingLink by mutableStateOf<Pairing?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneConfigStore(this)
        relay = Relay(this)
        collectors = store.load().collectors
        // With a single collector the form shows it, so the one-machine case reads as before.
        collectors.singleOrNull()?.let {
            url = it.url
            token = it.token.orEmpty()
        }
        // After a rotation the link intent is still here; only ask again if it was unanswered.
        if (savedInstanceState == null || savedInstanceState.getBoolean(KEY_LINK_PENDING)) handleLink(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LINK_PENDING, pendingLink != null)
    }

    /**
     * A link never saves on its own: any app or web page can fire `mana://pair`, and saving
     * would send the next fetch, token and all, wherever the link pointed.
     */
    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.dataString ?: return
        val pairing = parsePairingUri(data)
        if (pairing == null) {
            pendingLink = null
            result = "That pairing link is not valid. Run mana-collector pair again."
        } else {
            pendingLink = pairing
        }
    }

    private fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { code ->
                val pairing = parsePairingUri(code.rawValue.orEmpty())
                if (pairing == null) {
                    result = "That is not a Mana pairing code. Scan the code mana-collector pair prints."
                } else {
                    pair(pairing)
                }
            }
            // On a sideloaded install the scanner module downloads on first use, and a scan
            // started before it lands fails here. Trying again a moment later works.
            .addOnFailureListener { e ->
                result = "Could not start the scanner (${e.message ?: e.javaClass.simpleName}). Try again in a moment."
            }
    }

    private fun pair(pairing: Pairing) {
        url = pairing.url
        token = pairing.token
        saveAndTest()
    }

    /**
     * Adds the collector in the fields, or updates the one with the same URL. Returns it,
     * or null with the reason in [result].
     */
    private fun save(): Collector? {
        val u = url.trim()
        if (u.isEmpty()) {
            result = "Enter a collector URL first."
            return null
        }
        urlProblem(u)?.let {
            result = it
            return null
        }
        val c = Collector(u, token.trim().ifEmpty { null })
        val before = PhoneConfig(collectors)
        val next = before.with(c)
        store.save(next)
        collectors = next.collectors
        val dropped = before.collectors.map { it.url } - next.collectors.map { it.url }.toSet()
        result = if (dropped.isEmpty()) "Saved." else "Mana keeps $MAX_COLLECTORS collectors, so ${collectorLabel(dropped.first())} was removed."
        return c
    }

    /** Tests the collector just saved on its own, so the answer is about that machine. */
    private fun saveAndTest() {
        val c = save() ?: return
        result = "Testing…"
        lifecycleScope.launch { result = describe(relay.answer(PhoneConfig(listOf(c)))) }
    }

    private fun remove(c: Collector) {
        val next = PhoneConfig(collectors).without(c.url)
        store.save(next)
        collectors = next.collectors
        result = "Removed ${collectorLabel(c.url)}."
    }

    @Composable
    private fun Screen() {
        Column(
            Modifier.safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mana", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Relays Claude Code usage from your collector to your watch.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = ::scan, modifier = Modifier.fillMaxWidth()) { Text("Scan pairing code") }
            Text(
                "Or enter the collector URL and token by hand.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Collector URL") },
                placeholder = { Text("https://my-pc.tailnet.ts.net/v1/usage") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { save() }) { Text("Save") }
                OutlinedButton(onClick = ::saveAndTest) { Text("Save and test") }
            }
            if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodyLarge)
            if (collectors.isNotEmpty()) {
                Text(
                    if (collectors.size > 1) "Collectors (the newest reading wins)" else "Collector",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            collectors.forEach { c ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(collectorLabel(c.url), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { remove(c) }) { Text("Remove") }
                }
            }
            val last = store.lastWatchRequestAt
            Text(
                if (last == 0L) "The watch has not asked yet."
                else "Last watch request: " + DateFormat.getTimeInstance().format(Date(last)),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        pendingLink?.let { link ->
            AlertDialog(
                onDismissRequest = { pendingLink = null },
                title = { Text("Pair with this collector?") },
                text = {
                    Text(
                        "Mana will send its usage requests and token to\n\n${urlHost(link.url)}\n\n" +
                            "Only continue if you just opened a pairing link from your own collector.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingLink = null
                        pair(link)
                    }) { Text("Pair") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLink = null }) { Text("Cancel") }
                },
            )
        }
    }

    private companion object {
        const val KEY_LINK_PENDING = "linkPending"
    }
}

/** Why [url] cannot be saved, or null. An empty URL is fine: it clears the setting. */
fun urlProblem(url: String): String? = when {
    url.isEmpty() || cleartextAllowed(url) -> null
    url.startsWith("http://", ignoreCase = true) -> HTTP_REFUSED_MESSAGE
    else -> "Enter an https:// or http:// URL."
}

private fun describe(r: FetchResult): String = when (r) {
    is FetchResult.Ok -> {
        val now = Instant.now()
        val p = r.payload
        val five = formatCountdown(parseInstant(p.fiveHour?.resetsAt), now)?.let { ", resets in $it" } ?: ""
        "5-hour ${formatPercent(p.fiveHour)}$five\nWeek ${formatPercent(p.weekly)}\nSource: ${p.source}" +
            if (p.stale) " (stale)" else ""
    }
    FetchResult.Unauthorized -> "Token rejected (401)."
    is FetchResult.Unreachable -> when (r.reason) {
        "cleartext-refused" -> HTTP_REFUSED_MESSAGE
        "bad-url" -> "That collector URL is not valid."
        else -> "Collector unreachable. Is Tailscale on?"
    }
    is FetchResult.HttpError -> "Collector returned HTTP ${r.code}."
    is FetchResult.Unsupported -> "Collector schema ${r.schemaVersion} is newer than this app."
    FetchResult.Malformed -> "Collector sent something that is not a usage payload."
    FetchResult.NotConfigured -> "Enter a collector URL first."
    FetchResult.NoPhone, FetchResult.NoPhoneApp -> "Unexpected state."
}

/**
 * How a collector is named on screen: host, plus the port when the URL has one, so two
 * collectors on one machine can be told apart. Falls back to the URL itself.
 */
fun collectorLabel(url: String): String {
    val uri = runCatching { java.net.URI(url) }.getOrNull()
    val host = uri?.host ?: return url
    return if (uri.port == -1) host else "$host:${uri.port}"
}
