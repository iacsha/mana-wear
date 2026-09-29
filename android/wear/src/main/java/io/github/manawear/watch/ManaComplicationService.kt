package io.github.manawear.watch

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Payload
import io.github.manawear.shared.Window
import io.github.manawear.shared.formatPercent
import io.github.manawear.shared.isStale
import io.github.manawear.shared.parseInstant
import java.time.Instant

/**
 * One usage window as a complication. RANGED_VALUE carries the percentage for a ring;
 * SHORT_TEXT carries "28%". Both carry the reset as a countdown the watch face renders
 * itself, so no update is needed as it ticks down. The system asks every five minutes
 * (UPDATE_PERIOD_SECONDS in the manifest); each ask is one relay fetch.
 */
abstract class ManaComplicationService(
    private val label: String,
    private val window: (Payload) -> Window?,
) : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val source = usageSourceFor(this)
        val payload = when (val r = source.fetch()) {
            is FetchResult.Ok -> r.payload
            // Unreachable, no phone, and so on: show the last reading the phone left
            // behind rather than a blank slot. Its age still flags it as stale.
            else -> source.cached()
        } ?: return NoDataComplicationData()
        val w = window(payload) ?: return NoDataComplicationData()
        return build(request.complicationType, w, isStale(payload, Instant.now()))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Window(42.0, Instant.now().plusSeconds(99 * 60).toString()), stale = false)

    private fun build(type: ComplicationType, w: Window, stale: Boolean): ComplicationData? {
        val text = PlainComplicationText.Builder(formatPercent(w)).build()
        val title = if (stale) PlainComplicationText.Builder("stale").build() else countdown(w)
        val description = PlainComplicationText.Builder("$label usage ${formatPercent(w)}").build()
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(w.usedPercentage.toFloat().coerceIn(0f, 100f), 0f, 100f, description)
                    .setText(text)
                    .setTitle(title)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text, description)
                    .setTitle(title)
                    .build()
            else -> null
        }
    }

    private fun countdown(w: Window): ComplicationText =
        parseInstant(w.resetsAt)?.let {
            TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountDownTimeReference(it))
                .build()
        } ?: PlainComplicationText.Builder(label).build()
}

class FiveHourComplicationService : ManaComplicationService("5-hour", { it.fiveHour })

class WeeklyComplicationService : ManaComplicationService("Weekly", { it.weekly })
