package app.openpulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openpulse.core.chart.ChartMath
import app.openpulse.core.model.DataPoint
import app.openpulse.core.time.TimeWindow
import app.openpulse.core.time.TimelineAxis
import app.openpulse.ui.format.Formats
import java.time.ZoneId
import kotlin.math.abs

/** One plotted series. Values must already be in the display unit. */
data class ChartSeries(
    val id: String,
    val label: String,
    val color: Color,
    val points: List<DataPoint>,
)

data class ChartSelection(val seriesId: String, val point: DataPoint)

/** Visible time range of a chart; supports zoom and pan within [bounds]. */
@Stable
class ChartViewport(initial: TimeWindow, bounds: TimeWindow) {
    var start by mutableLongStateOf(initial.start)
        private set
    var end by mutableLongStateOf(initial.end)
        private set
    var boundsStart by mutableLongStateOf(minOf(bounds.start, initial.start))
        private set
    var boundsEnd by mutableLongStateOf(maxOf(bounds.end, initial.end))
        private set
    private var initialWindow = initial

    val isModified: Boolean get() = start != initialWindow.start || end != initialWindow.end
    val span: Long get() = end - start

    fun reset(window: TimeWindow = initialWindow, bounds: TimeWindow? = null) {
        initialWindow = window
        start = window.start; end = window.end
        if (bounds != null) { boundsStart = minOf(bounds.start, window.start); boundsEnd = maxOf(bounds.end, window.end) }
    }

    fun zoom(factor: Float, focusFraction: Float) {
        if (factor <= 0f || factor.isNaN()) return
        val total = boundsEnd - boundsStart
        val newSpan = (span / factor).toLong().coerceIn(MIN_SPAN, maxOf(total, MIN_SPAN))
        val focus = start + (span * focusFraction).toLong()
        var ns = focus - ((focus - start) * (newSpan.toDouble() / span)).toLong()
        ns = ns.coerceIn(boundsStart, maxOf(boundsStart, boundsEnd - newSpan))
        start = ns; end = ns + newSpan
    }

    fun pan(fraction: Float) {
        val shift = (-fraction * span).toLong()
        val ns = (start + shift).coerceIn(boundsStart, maxOf(boundsStart, boundsEnd - span))
        end = ns + span; start = ns
    }

    companion object { const val MIN_SPAN = 2 * 3_600_000L }
}

@Composable
fun rememberChartViewport(window: TimeWindow, bounds: TimeWindow): ChartViewport {
    // A new range (or new data bounds) starts a fresh viewport showing exactly that range.
    return remember(window.start, window.end, bounds.start, bounds.end) { ChartViewport(window, bounds) }
}

/**
 * Time-series chart that plots ONLY recorded measurements as dots on a continuous time axis.
 *
 * - Days without data keep their space on the axis (with day gridlines and a coverage strip).
 * - Optional connector lines are thin and dashed, and are broken whenever two consecutive
 *   recordings are more than [connectMaxGapMillis] apart - nothing is interpolated.
 * - Pinch to zoom, drag horizontally to pan, tap a dot to inspect it.
 */
@Composable
fun HealthChart(
    series: List<ChartSeries>,
    viewport: ChartViewport,
    zone: ZoneId,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    connectMaxGapMillis: Long? = 3 * 86_400_000L,
    referenceBand: ClosedFloatingPointRange<Double>? = null,
    selection: ChartSelection? = null,
    onSelect: (ChartSelection?) -> Unit = {},
    interactive: Boolean = true,
    showCoverage: Boolean = true,
    emptyText: String = "No measurements in this period",
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val currentSeries by rememberUpdatedState(series)
    val currentSelect by rememberUpdatedState(onSelect)

    val visibleCount = series.sumOf { s -> s.points.count { it.time in viewport.start until viewport.end } }
    val description = "Chart: ${series.joinToString { it.label }}. $visibleCount recorded values between " +
        "${Formats.date(viewport.start, zone)} and ${Formats.date(viewport.end - 1, zone)}."

    Box(modifier.fillMaxWidth().height(height)) {
        var gestureMod: Modifier = Modifier
        if (interactive) {
            gestureMod = Modifier
                .pointerInput(viewport) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var panning = false
                        var dxTotal = 0f
                        var dyTotal = 0f
                        val plotWidth = (size.width - LEFT_AXIS_PX(density) - RIGHT_PAD_PX(density)).coerceAtLeast(1f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val pan = event.calculatePan()
                                val focus = ((centroid.x - LEFT_AXIS_PX(density)) / plotWidth).coerceIn(0f, 1f)
                                viewport.zoom(zoom, focus)
                                viewport.pan(pan.x / plotWidth)
                                panning = true
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                val d = change.positionChange()
                                if (!panning) {
                                    dxTotal += d.x; dyTotal += d.y
                                    if (abs(dxTotal) > viewConfiguration.touchSlop && abs(dxTotal) > abs(dyTotal)) {
                                        panning = true
                                    } else if (abs(dyTotal) > viewConfiguration.touchSlop) {
                                        break // vertical: let the page scroll
                                    }
                                }
                                if (panning) {
                                    viewport.pan(d.x / plotWidth)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                .pointerInput(viewport, zone) {
                    detectTapGestures { pos ->
                        val plot = plotRect(Size(size.width.toFloat(), size.height.toFloat()), density)
                        val geo = Geometry.of(currentSeries, viewport, plot, referenceBandOf = null)
                        val xs = mutableListOf<Float>(); val ys = mutableListOf<Float>(); val refs = mutableListOf<ChartSelection>()
                        currentSeries.forEach { s ->
                            s.points.filter { it.time in viewport.start until viewport.end }.forEach { p ->
                                xs += geo.x(p.time); ys += geo.y(p.value); refs += ChartSelection(s.id, p)
                            }
                        }
                        val idx = ChartMath.nearestIndex(xs, ys, pos.x, pos.y, maxDistance = 28.dp.toPx())
                        currentSelect(idx?.let { refs[it] })
                    }
                }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(gestureMod)
                .semantics { contentDescription = description },
        ) {
            val plot = plotRect(size, density)
            val geo = Geometry.of(series, viewport, plot, referenceBand)
            drawGrid(geo, plot, measurer, labelStyle, yLabel, zone, colors.outlineVariant, viewport)
            if (referenceBand != null) {
                val top = geo.y(referenceBand.endInclusive).coerceIn(plot.top, plot.bottom)
                val bottom = geo.y(referenceBand.start).coerceIn(plot.top, plot.bottom)
                if (bottom > top) drawRect(colors.primary.copy(alpha = 0.06f), Offset(plot.left, top), Size(plot.width, bottom - top))
            }
            if (showCoverage) drawCoverage(series, viewport, plot, zone, colors.primary, colors.outlineVariant)

            clipRect(plot.left - 6.dp.toPx(), plot.top - 6.dp.toPx(), plot.right + 6.dp.toPx(), plot.bottom + 6.dp.toPx()) {
                // Connector lines (subtle, dashed) between real consecutive readings only.
                if (connectMaxGapMillis != null) {
                    series.forEach { s ->
                        val margin = viewport.span / 2
                        val near = s.points.filter { it.time >= viewport.start - margin && it.time < viewport.end + margin }
                        ChartMath.connectableRuns(near, connectMaxGapMillis).filter { it.size > 1 }.forEach { run ->
                            val path = Path()
                            run.forEachIndexed { i, p -> if (i == 0) path.moveTo(geo.x(p.time), geo.y(p.value)) else path.lineTo(geo.x(p.time), geo.y(p.value)) }
                            drawPath(
                                path, s.color.copy(alpha = 0.38f),
                                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                            )
                        }
                    }
                }
                // Selected guide line
                selection?.let { sel ->
                    val x = geo.x(sel.point.time)
                    if (x in plot.left..plot.right) {
                        drawLine(colors.onSurfaceVariant.copy(alpha = 0.5f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
                    }
                }
                // Dots: the actual recorded measurements.
                val r = if (viewport.span > 200L * 86_400_000L) 3.dp.toPx() else 4.2.dp.toPx()
                series.forEach { s ->
                    s.points.forEach { p ->
                        if (p.time < viewport.start || p.time >= viewport.end) return@forEach
                        val c = Offset(geo.x(p.time), geo.y(p.value))
                        drawCircle(colors.surfaceContainerLow, radius = r + 1.6.dp.toPx(), center = c)
                        drawCircle(s.color, radius = r, center = c)
                    }
                }
                selection?.let { sel ->
                    val s = series.firstOrNull { it.id == sel.seriesId } ?: return@let
                    val c = Offset(geo.x(sel.point.time), geo.y(sel.point.value))
                    drawCircle(s.color.copy(alpha = 0.18f), radius = 13.dp.toPx(), center = c)
                    drawCircle(s.color, radius = 6.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                }
            }
        }
        if (visibleCount == 0) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun LEFT_AXIS_PX(density: Float) = 44f * density
private fun RIGHT_PAD_PX(density: Float) = 10f * density

private fun plotRect(size: Size, density: Float): Rect = Rect(
    left = LEFT_AXIS_PX(density),
    top = 12f * density,
    right = size.width - RIGHT_PAD_PX(density),
    bottom = size.height - 30f * density,
)

private class Geometry(val plot: Rect, val start: Long, val end: Long, val scale: ChartMath.Scale) {
    fun x(t: Long): Float = plot.left + ((t - start).toDouble() / (end - start).coerceAtLeast(1)).toFloat() * plot.width
    fun y(v: Double): Float = plot.bottom - ((v - scale.min) / (scale.max - scale.min)).toFloat() * plot.height

    companion object {
        fun of(series: List<ChartSeries>, vp: ChartViewport, plot: Rect, referenceBandOf: ClosedFloatingPointRange<Double>?): Geometry {
            val visible = series.flatMap { s -> s.points.filter { it.time in vp.start until vp.end } }.map { it.value }
            val values = visible.ifEmpty { series.flatMap { s -> s.points.map { it.value } } }
            val lo = values.minOrNull() ?: referenceBandOf?.start ?: 0.0
            val hi = values.maxOrNull() ?: referenceBandOf?.endInclusive ?: 10.0
            val minSpan = maxOf(1.0, abs(hi) * 0.04)
            return Geometry(plot, vp.start, vp.end, ChartMath.niceScale(lo, hi, 5, minSpan))
        }
    }
}

private fun DrawScope.drawGrid(
    geo: Geometry, plot: Rect, measurer: TextMeasurer, style: TextStyle,
    yLabel: (Double) -> String, zone: ZoneId, gridColor: Color, vp: ChartViewport,
) {
    // Day boundaries: faint lines so empty days remain visible.
    val days = TimelineAxis.dayBoundaries(vp.start, vp.end, zone, maxDays = 62)
    days.forEach { t ->
        val x = geo.x(t)
        drawLine(gridColor.copy(alpha = 0.35f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 0.6.dp.toPx())
    }
    // Horizontal value grid + labels
    geo.scale.ticks.forEach { v ->
        val y = geo.y(v)
        if (y < plot.top - 1 || y > plot.bottom + 1) return@forEach
        drawLine(gridColor.copy(alpha = 0.7f), Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 0.8.dp.toPx())
        val layout = measurer.measure(yLabel(v), style)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
    }
    // Time ticks + labels
    val maxTicks = (plot.width / 64.dp.toPx()).toInt().coerceIn(2, 8)
    TimelineAxis.ticks(vp.start, vp.end, zone, maxTicks).forEach { tick ->
        val x = geo.x(tick.time)
        drawLine(gridColor, Offset(x, plot.bottom), Offset(x, plot.bottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
        val layout = measurer.measure(Formats.tick(tick.time, tick.granularity, zone), style)
        val lx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(lx, plot.bottom + 12.dp.toPx()))
    }
    drawLine(gridColor, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
}

/** Thin strip under the plot: filled where a day has at least one recording, empty otherwise. */
private fun DrawScope.drawCoverage(series: List<ChartSeries>, vp: ChartViewport, plot: Rect, zone: ZoneId, filled: Color, empty: Color) {
    val days = TimelineAxis.dayBoundaries(vp.start, vp.end, zone, maxDays = 190)
    if (days.isEmpty()) return
    val dayKeys = series.flatMap { it.points }.map { java.time.Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }.toHashSet()
    val y = plot.bottom + 3.dp.toPx()
    val h = 3.dp.toPx()
    days.forEachIndexed { i, start ->
        val endT = days.getOrNull(i + 1) ?: vp.end
        val x0 = plot.left + ((start - vp.start).toDouble() / vp.span).toFloat() * plot.width
        val x1 = plot.left + ((endT - vp.start).toDouble() / vp.span).toFloat() * plot.width
        val has = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate() in dayKeys
        val gap = if (x1 - x0 > 3.dp.toPx()) 0.8.dp.toPx() else 0f
        drawRect(if (has) filled.copy(alpha = 0.55f) else empty.copy(alpha = 0.35f), Offset(x0 + gap, y), Size((x1 - x0 - 2 * gap).coerceAtLeast(0.5f), h))
    }
}
