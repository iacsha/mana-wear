package io.github.claudeclip.watch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

const val REFRESH_MS = 60_000L

/**
 * One screen. Configure from a computer with:
 *
 *   adb shell am start -n io.github.claudeclip.watch/.MainActivity \
 *     --es url http://HOST:7339/v1/usage --es token TOKEN
 *
 * Nothing here logs the URL, the token, or any figure.
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: ConfigStore
    private var config by mutableStateOf(Config(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        config = store.load()
        absorb(intent)

        setContent {
            var state by remember(config) { mutableStateOf(UsageState(configured = config.url != null)) }
            var now by remember { mutableStateOf(Instant.now()) }
            val source = remember(config) { config.url?.let { HttpUsageSource(it, config.token) } }
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(source) {
                source ?: return@LaunchedEffect
                // Fetch on every resume, then once a minute while the screen is visible.
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        state = state.copy(loading = true)
                        state = state.after(source.fetch())
                        now = Instant.now()
                        delay(REFRESH_MS)
                    }
                }
            }
            LaunchedEffect(Unit) {
                while (true) {
                    now = Instant.now()
                    delay(5_000)
                }
            }

            UsageScreen(state, now)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        absorb(intent)
    }

    private fun absorb(intent: Intent?) {
        intent ?: return
        val next = applyConfig(config, intent.getStringExtra("url"), intent.getStringExtra("token"))
        if (next != config) {
            store.save(next)
            config = next
        }
    }
}
