package io.github.manawear.watch

import io.github.manawear.shared.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import java.time.Instant

@Composable
fun UsageScreen(state: UsageState, now: Instant) {
    MaterialTheme {
        AppScaffold {
            ScreenScaffold {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        state.lastGood == null && state.problem == Problem.NOT_CONFIGURED -> SetupHint()
                        state.lastGood == null -> Waiting(state)
                        else -> Reading(state.lastGood, state.problem, now)
                    }
                }
            }
        }
    }
}

@Composable
private fun Reading(payload: Payload, problem: Problem?, now: Instant) {
    val five = payload.fiveHour
    CircularProgressIndicator(
        progress = { ((five?.usedPercentage ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxSize().padding(4.dp),
        startAngle = 120f,
        endAngle = 60f,
    )
    Column(
        Modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("5-hour", style = MaterialTheme.typography.labelSmall)
        Text(formatPercent(five), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        formatCountdown(parseInstant(five?.resetsAt), now)?.let {
            Text("resets $it", style = MaterialTheme.typography.bodySmall)
        }
        val weekly = payload.weekly
        val weeklyReset = formatCountdown(parseInstant(weekly?.resetsAt), now)
        Text(
            "week ${formatPercent(weekly)}" + (weeklyReset?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            statusLine(payload, problem, now),
            style = MaterialTheme.typography.labelSmall,
            color = if (problem != null || isStale(payload, now)) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusLine(payload: Payload, problem: Problem?, now: Instant): String {
    val age = ageSeconds(parseInstant(payload.observedAt), now)?.let { "${formatAge(it)} ago" } ?: "age unknown"
    return when {
        problem != null -> "${problem.label()} · $age"
        isStale(payload, now) -> "stale · $age"
        else -> age
    }
}

@Composable
private fun Waiting(state: UsageState) {
    Text(
        state.problem?.let { p -> p.label() + (state.detail?.let { "\n$it" } ?: "") } ?: "loading…",
        modifier = Modifier.padding(horizontal = 24.dp),
        textAlign = TextAlign.Center,
        color = if (state.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SetupHint() {
    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Not set up", style = MaterialTheme.typography.titleMedium)
        Text(
            "Open Mana on your phone and enter the collector URL and token.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
