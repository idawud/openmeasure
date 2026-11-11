package app.openmeasure.measure.ruler

import app.openmeasure.measure.units.UnitSystem

enum class TickKind { MAJOR, MEDIUM, MINOR }

/** A tick [positionMeters] from the segment start. [label] is only set on labelled major ticks. */
data class RulerTick(val positionMeters: Double, val kind: TickKind, val label: String?)

/**
 * Ruler-style tick scale along a measured segment (F13). Pure maths: picks the finest "nice" step that keeps
 * ticks at least [MIN_TICK_PX] apart on screen, so ticks appear as the user moves closer.
 * Metric steps: 1 mm, 5 mm, 1 cm, 5 cm, 10 cm, 50 cm, 1 m (major every 10 cm, or 1 m when zoomed out).
 * Imperial steps: 1/16, 1/8, 1/4, 1/2, 1, 6 in, 1 ft (major every 1 in, or 1 ft when zoomed out).
 */
object RulerTicks {
    const val MIN_TICK_PX = 8.0
    const val MIN_SEGMENT_PX = 40.0
    const val MIN_LABEL_PX = 36.0
    private const val MAX_TICKS = 1500

    private class Scale(val unitMeters: Double, val steps: LongArray, val majors: LongArray)

    // Metric unit = 1 mm; imperial unit = 1/16 in.
    private val METRIC = Scale(0.001, longArrayOf(1, 5, 10, 50, 100, 500, 1000), longArrayOf(100, 1000))
    private val IMPERIAL = Scale(0.0254 / 16.0, longArrayOf(1, 2, 4, 8, 16, 96, 192), longArrayOf(16, 192))

    fun generate(lengthMeters: Double, unitSystem: UnitSystem, pixelsPerMeter: Double): List<RulerTick> {
        if (!lengthMeters.isFinite() || lengthMeters <= 0.0) return emptyList()
        if (!pixelsPerMeter.isFinite() || pixelsPerMeter <= 0.0) return emptyList()
        if (lengthMeters * pixelsPerMeter < MIN_SEGMENT_PX) return emptyList()
        val sc = if (unitSystem == UnitSystem.METRIC) METRIC else IMPERIAL
        val pxPerUnit = pixelsPerMeter * sc.unitMeters
        val step = sc.steps.firstOrNull { it * pxPerUnit >= MIN_TICK_PX } ?: return emptyList()
        val major = if (step < sc.majors[0]) sc.majors[0] else sc.majors[1]
        val medium = mediumStep(sc, step, major)
        // Labels: the finest major-or-coarser step that leaves room for text.
        val labelStep = sc.majors.firstOrNull { it >= major && it * pxPerUnit >= MIN_LABEL_PX }
        val total = (lengthMeters / sc.unitMeters + 1e-9).toLong()
        if (total / step > MAX_TICKS) return emptyList()
        val out = ArrayList<RulerTick>()
        var u = step
        while (u <= total) {
            val kind = when {
                u % major == 0L -> TickKind.MAJOR
                medium != null && u % medium == 0L -> TickKind.MEDIUM
                else -> TickKind.MINOR
            }
            val label = if (labelStep != null && u % labelStep == 0L) label(u, sc === METRIC) else null
            out += RulerTick(u * sc.unitMeters, kind, label)
            u += step
        }
        return out
    }

    private fun mediumStep(sc: Scale, step: Long, major: Long): Long? {
        val m = if (sc === IMPERIAL && step < 8) 8L else sc.steps.firstOrNull { it > step }
        return m?.takeIf { it < major }
    }

    private fun label(u: Long, metric: Boolean): String = if (metric) {
        when {
            u < 1000 -> "${u / 10} cm"
            u % 1000 == 0L -> "${u / 1000} m"
            else -> "${u / 1000}.${(u % 1000) / 100} m"
        }
    } else {
        if (u % 192 == 0L) "${u / 192} ft" else "${u / 16} in"
    }
}
