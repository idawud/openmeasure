package app.openmeasure.measure.projection

/** Picks which projected point (if any) a touch grabbed. Pure maths, JVM-testable. */
object PointPicker {
    /**
     * Index of the point nearest to ([x], [y]) within [radiusPx] (inclusive); null if none.
     * Null entries (behind the camera) are skipped. Exact distance ties resolve to the lowest index.
     */
    fun pick(points: List<ScreenPoint?>, x: Float, y: Float, radiusPx: Float): Int? {
        var best: Int? = null
        var bestD2 = radiusPx * radiusPx
        for (i in points.indices) {
            val p = points[i] ?: continue
            val dx = p.x - x
            val dy = p.y - y
            val d2 = dx * dx + dy * dy
            if (d2 <= bestD2 && (best == null || d2 < bestD2)) {
                best = i
                bestD2 = d2
            }
        }
        return best
    }
}
