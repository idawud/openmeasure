package app.openmeasure.measure.model

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.sqrt

/** Stable identifier of a measured point within a [app.openmeasure.measure.session.MeasurementSession]. */
@JvmInline
value class PointId(val value: Long)

/**
 * A measured 3D point in world space (metres).
 * [confidence] is optional (0..1); null means "unknown" (e.g. plane hit without depth confidence).
 */
data class Point(
    val id: PointId,
    val position: Vec3,
    val confidence: Float? = null,
) {
    init {
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) {
            "Point position must be finite: $position"
        }
        require(confidence == null || confidence in 0f..1f) { "confidence must be in 0..1: $confidence" }
    }
}

/** Straight segment between two points. */
data class Segment(val start: Point, val end: Point) {
    /** Euclidean length in metres, computed in double precision to avoid float cancellation far from origin. */
    val lengthMeters: Double = distance(start.position, end.position)

    /** Below [DEGENERATE_LENGTH_M] the segment is effectively two coincident points. */
    val isDegenerate: Boolean get() = lengthMeters < DEGENERATE_LENGTH_M

    /** Weakest endpoint confidence; null if neither endpoint reports one. */
    val confidence: Float?
        get() = when {
            start.confidence == null -> end.confidence
            end.confidence == null -> start.confidence
            else -> minOf(start.confidence, end.confidence)
        }

    companion object {
        /** 0.1 mm: well below the 1 cm accuracy target, above float noise at room scale. */
        const val DEGENERATE_LENGTH_M = 1e-4

        internal fun distance(a: Vec3, b: Vec3): Double {
            val dx = a.x.toDouble() - b.x.toDouble()
            val dy = a.y.toDouble() - b.y.toDouble()
            val dz = a.z.toDouble() - b.z.toDouble()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}

/**
 * Ordered polyline through [points]. If [closed] and there are at least 3 points, a closing
 * segment last -> first is appended. With 0 or 1 points there are no segments and length is 0.
 */
data class Path(val points: List<Point>, val closed: Boolean = false) {
    /** True only when the closing segment actually exists (closed flag and >= 3 points). */
    val isEffectivelyClosed: Boolean get() = closed && points.size >= 3

    val segments: List<Segment> = buildList {
        for (i in 0 until points.size - 1) add(Segment(points[i], points[i + 1]))
        if (closed && points.size >= 3) add(Segment(points.last(), points.first()))
    }

    val totalLengthMeters: Double = segments.sumOf { it.lengthMeters }

    /** Running totals: element i = sum of segment lengths 0..i (for "running total" labels). */
    val cumulativeLengthsMeters: List<Double> = segments.runningFold(0.0) { acc, s -> acc + s.lengthMeters }.drop(1)

    companion object {
        val EMPTY = Path(emptyList())
    }
}
