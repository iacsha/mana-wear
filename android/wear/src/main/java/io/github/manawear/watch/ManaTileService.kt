package io.github.manawear.watch

import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Payload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private const val RESOURCES_VERSION = "1"
private const val FRESHNESS_MS = 5 * 60 * 1000L
private const val SWEEP = 280f
private const val START = -140f

private const val BLUE = 0xFF4FA3FF.toInt()
private const val BLUE_TRACK = 0xFF16233D.toInt()
private const val VIOLET = 0xFF9B7BFF.toInt()
private const val VIOLET_TRACK = 0xFF221A3D.toInt()
private const val DIM = 0xFF8FB8FF.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val WARN = 0xFFFFB4A9.toInt()

/**
 * The Mana tile: the same two rings as the watch face, with the five-hour figure large in
 * the middle. Tapping it opens the app. Data comes the same way as the complications.
 */
class ManaTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                val root = try {
                    layout(load())
                } catch (e: Exception) {
                    layout(null)
                }
                completer.set(
                    TileBuilders.Tile.Builder()
                        .setResourcesVersion(RESOURCES_VERSION)
                        .setFreshnessIntervalMillis(FRESHNESS_MS)
                        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                        .build()
                )
            }
            "mana-tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "mana-tile-resources"
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun load(): Payload? {
        val source = usageSourceFor(this)
        return when (val r = source.fetch()) {
            is FetchResult.Ok -> r.payload
            else -> source.cached()
        }
    }

    private fun layout(payload: Payload?): LayoutElement {
        val body = Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        val box = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(openApp())

        if (payload == null) {
            body.addContent(text("Mana", 16f, DIM))
                .addContent(spacer(6f))
                .addContent(text("No reading yet", 14f, WHITE))
                .addContent(text("Tap to open", 12f, DIM))
            return box.addContent(body.build()).build()
        }

        val m = tileModel(payload, Instant.now(), ZoneId.systemDefault(), DateFormat.is24HourFormat(this))
        box.addContent(ring(1f, BLUE_TRACK, 10f))
            .addContent(ring(m.fiveHourRemaining, BLUE, 10f))
            .addContent(inset(ring(1f, VIOLET_TRACK, 5f)))
            .addContent(inset(ring(m.weeklyRemaining, VIOLET, 5f)))

        body.addContent(text("5-hour", 13f, DIM))
            .addContent(text(m.fiveHourPercent, 40f, WHITE, bold = true))
            .addContent(text(m.fiveHourReset, 13f, BLUE))
            .addContent(spacer(6f))
            .addContent(text(m.weeklyLine, 13f, VIOLET))
        if (m.stale) body.addContent(text("stale", 12f, WARN))
        return box.addContent(body.build()).build()
    }

    private fun ring(fraction: Float, color: Int, thickness: Float): LayoutElement =
        Arc.Builder()
            .setAnchorAngle(degrees(START))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(SWEEP * fraction.coerceIn(0f, 1f)))
                    .setThickness(dp(thickness))
                    .setColor(argb(color))
                    .build()
            )
            .build()

    /** The weekly ring sits inside the five-hour ring, as on the watch face. */
    private fun inset(ring: LayoutElement): LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(14f)).build())
                    .build()
            )
            .addContent(ring)
            .build()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): LayoutElement =
        Text.Builder()
            .setText(value)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    private fun spacer(height: Float): LayoutElement = Spacer.Builder().setHeight(dp(height)).build()

    private fun openApp(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .setClassName(MainActivity::class.java.name)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
}
