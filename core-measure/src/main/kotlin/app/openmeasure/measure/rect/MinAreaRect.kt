package app.openmeasure.measure.rect

/** Oriented rectangle in 2D; [corners] counter-clockwise; [width] >= [height]. */
data class Rect2(val corners: List<Vec2>, val width: Double, val height: Double) {
    val area: Double get() = width * height
}

/** Convex hull and minimum-area enclosing rectangle (rotating calipers over hull edge directions). */
object MinAreaRect {

    /** Andrew's monotone chain; counter-clockwise, no collinear points. Fewer than 3 distinct points -> as-is. */
    fun convexHull(points: List<Vec2>): List<Vec2> {
        val p = points.distinct().sortedWith(compareBy<Vec2> { it.x }.thenBy { it.y })
        if (p.size < 3) return p
        val hull = ArrayList<Vec2>(2 * p.size)
        for (pt in p) {
            while (hull.size >= 2 && turn(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
            hull += pt
        }
        val lower = hull.size + 1
        for (i in p.size - 2 downTo 0) {
            val pt = p[i]
            while (hull.size >= lower && turn(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
            hull += pt
        }
        hull.removeAt(hull.size - 1)
        return hull
    }

    /**
     * Minimum-area rectangle enclosing [points] (one of its sides is collinear with a hull edge, so testing every
     * hull edge direction is exact). O(h^2) in hull size h, which stays in the low hundreds for depth regions.
     * Null for fewer than 3 non-collinear points.
     */
    fun of(points: List<Vec2>): Rect2? {
        val hull = convexHull(points)
        if (hull.size < 3) return null
        var best: Rect2? = null
        for (i in hull.indices) {
            val e = hull[(i + 1) % hull.size] - hull[i]
            val len = e.length()
            if (len < 1e-12) continue
            val d = e * (1.0 / len)
            val n = Vec2(-d.y, d.x)
            var minD = Double.POSITIVE_INFINITY; var maxD = Double.NEGATIVE_INFINITY
            var minN = Double.POSITIVE_INFINITY; var maxN = Double.NEGATIVE_INFINITY
            for (q in hull) {
                val a = q dot d
                val b = q dot n
                if (a < minD) minD = a
                if (a > maxD) maxD = a
                if (b < minN) minN = b
                if (b > maxN) maxN = b
            }
            val area = (maxD - minD) * (maxN - minN)
            if (best == null || area < best.area) {
                val c = listOf(
                    d * minD + n * minN, d * maxD + n * minN, d * maxD + n * maxN, d * minD + n * maxN,
                )
                val w = maxD - minD
                val h = maxN - minN
                best = Rect2(c, maxOf(w, h), minOf(w, h))
            }
        }
        return best
    }

    private fun turn(o: Vec2, a: Vec2, b: Vec2): Double = (a - o) cross (b - o)
}
