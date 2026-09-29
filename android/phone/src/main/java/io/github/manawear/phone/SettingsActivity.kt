package io.github.manawear.phone

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.formatCountdown
import io.github.manawear.shared.formatPercent
import io.github.manawear.shared.parseInstant
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Where the collector URL and token are entered. The watch never sees the token. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PhoneConfigStore(this)
        val relay = Relay(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val saved = remember { store.load() }
                    var url by remember { mutableStateOf(saved.url.orEmpty()) }
                    var token by remember { mutableStateOf(saved.token.orEmpty()) }
                    var result by remember { mutableStateOf("") }
                    val scope = rememberCoroutineScope()

                    Column(
                        Modifier.safeDrawingPadding().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Mana", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Relays Claude Code usage from your collector to your watch.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Collector URL") },
                            placeholder = { Text("http://100.x.y.z:7339/v1/usage") },
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
                            Button(onClick = {
                                store.save(PhoneConfig(url.trim().ifEmpty { null }, token.trim().ifEmpty { null }))
                                result = "Saved."
                            }) { Text("Save") }
                            OutlinedButton(onClick = {
                                store.save(PhoneConfig(url.trim().ifEmpty { null }, token.trim().ifEmpty { null }))
                                result = "Testing…"
                                scope.launch { result = describe(relay.answer()) }
                            }) { Text("Save and test") }
                        }
                        if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodyLarge)
                        val last = store.lastWatchRequestAt
                        Text(
                            if (last == 0L) "The watch has not asked yet."
                            else "Last watch request: " + DateFormat.getTimeInstance().format(Date(last)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
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
    is FetchResult.Unreachable -> "Collector unreachable. Is Tailscale on?"
    is FetchResult.HttpError -> "Collector returned HTTP ${r.code}."
    is FetchResult.Unsupported -> "Collector schema ${r.schemaVersion} is newer than this app."
    FetchResult.Malformed -> "Collector sent something that is not a usage payload."
    FetchResult.NotConfigured -> "Enter a collector URL first."
    FetchResult.NoPhone, FetchResult.NoPhoneApp -> "Unexpected state."
}
