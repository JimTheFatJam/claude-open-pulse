package app.openpulse.core.chart

import app.openpulse.core.model.DataPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Helpers for honest charting: only real points, explicit gaps, nice axis scales. */
object ChartMath {
    /**
     * Splits sorted points into runs that may be joined by a connector line. Consecutive
     * recorded points farther apart than [maxGapMillis] start a new run, leaving a visible gap.
     * When [maxGapMillis] is null, no points are connected (each run has one point).
     * Nothing is ever inserted between points.
     */
    fun connectableRuns(points: List<DataPoint>, maxGapMillis: Long?): List<List<DataPoint>> {
        val sorted = points.sortedBy { it.time }
        if (sorted.isEmpty()) return emptyList()
        if (maxGapMillis == null) return sorted.map { listOf(it) }
        val runs = mutableListOf<MutableList<DataPoint>>(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            val p = sorted[i]
            if (p.time - sorted[i - 1].time > maxGapMillis) runs += mutableListOf(p) else runs.last() += p
        }
        return runs
    }

    data class Scale(val min: Double, val max: Double, val step: Double) {
        val ticks: List<Double>
            get() {
                val out = mutableListOf<Double>()
                var v = min
                var guard = 0
                while (v <= max + step * 1e-9 && guard++ < 100) { out += v; v += step }
                return out
            }
    }

    /** A "nice" axis scale covering [lo, hi] with roughly [targetTicks] ticks. */
    fun niceScale(lo: Double, hi: Double, targetTicks: Int = 5, minSpan: Double = 1.0): Scale {
        var a = minOf(lo, hi); var b = maxOf(lo, hi)
        if (b - a < minSpan) {
            val mid = (a + b) / 2; a = mid - minSpan / 2; b = mid + minSpan / 2
        }
        val step = niceNumber((b - a) / (targetTicks - 1).coerceAtLeast(1), round = true)
        val min = floor(a / step) * step
        val max = ceil(b / step) * step
        return Scale(min, max, step)
    }

    private fun niceNumber(range: Double, round: Boolean): Double {
        if (range <= 0.0) return 1.0
        val exponent = floor(log10(range))
        val fraction = range / 10.0.pow(exponent)
        val nice = if (round) {
            when { fraction < 1.5 -> 1.0; fraction < 3 -> 2.0; fraction < 7 -> 5.0; else -> 10.0 }
        } else {
            when { fraction <= 1 -> 1.0; fraction <= 2 -> 2.0; fraction <= 5 -> 5.0; else -> 10.0 }
        }
        return nice * 10.0.pow(exponent)
    }

    /** Index of the point nearest to [x] (in screen units) within [maxDistance], or null. */
    fun nearestIndex(xs: List<Float>, ys: List<Float>, x: Float, y: Float, maxDistance: Float): Int? {
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        for (i in xs.indices) {
            val dx = xs[i] - x; val dy = ys[i] - y
            val d = dx * dx + dy * dy * 0.25f // favour horizontal proximity
            if (d < bestD) { bestD = d; best = i }
        }
        return best?.takeIf { abs(xs[it] - x) <= maxDistance }
    }
}
