package app.openmeasure.measure.room

import app.openmeasure.measure.label.LabelBox
import app.openmeasure.measure.label.LabelLayout
import app.openmeasure.measure.projection.ScreenPoint
import kotlin.math.abs

/** Measured size of one label in output units (px, pt, ...). */
data class LabelSize(val w: Float, val h: Float)

/**
 * A plan fitted into a viewport (y down): [corners] parallel to [RoomPlan.corners]; [wallLabels] = label CENTRES,
 * one per wall, outside the room and de-overlapped; [center] = polygon centroid for the area label;
 * [pxPerMeter] = uniform scale (never stretched).
 */
data class PlanScreen(
    val corners: List<ScreenPoint>,
    val wallLabels: List<ScreenPoint>,
    val center: ScreenPoint,
    val pxPerMeter: Float,
)

object PlanLayout {
    /**
     * Uniformly scales and centres [plan] inside [width] x [height] with [padding] on every side (leave room for
     * the outside labels). Each wall label sits [labelGap] beyond the wall along its outward normal, pushed out by
     * half its own [labelSizes] extent, then nudged apart with [LabelLayout] and clamped into the viewport.
     * [labelSizes] null or short -> zero-size labels (pure anchor points).
     */
    fun fit(
        plan: RoomPlan,
        width: Float,
        height: Float,
        padding: Float,
        labelGap: Float = 8f,
        labelSizes: List<LabelSize>? = null,
    ): PlanScreen {
        val pts = plan.corners
        if (pts.isEmpty()) return PlanScreen(emptyList(), emptyList(), ScreenPoint(width / 2f, height / 2f), 0f)
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val availW = (width - 2 * padding).coerceAtLeast(1f)
        val availH = (height - 2 * padding).coerceAtLeast(1f)
        val spanX = maxX - minX
        val spanY = maxY - minY
        val scale = when {
            spanX < EPS && spanY < EPS -> 1.0
            spanX < EPS -> availH / spanY
            spanY < EPS -> availW / spanX
            else -> minOf(availW / spanX, availH / spanY)
        }
        val ox = width / 2.0 - (minX + maxX) / 2.0 * scale
        val oy = height / 2.0 + (minY + maxY) / 2.0 * scale // y flips: screen y = oy - planY * scale
        fun map(p: Vec2) = ScreenPoint((ox + p.x * scale).toFloat(), (oy - p.y * scale).toFloat())
        val screen = pts.map(::map)

        val n = pts.size
        val walls = if (n < 2) 0 else plan.wallLengths.size
        val boxes = (0 until walls).map { i ->
            val a = screen[i]
            val b = screen[(i + 1) % n]
            val size = labelSizes?.getOrNull(i) ?: LabelSize(0f, 0f)
            val (nx, ny) = outwardNormal(a, b)
            val push = labelGap + abs(nx) * size.w / 2f + abs(ny) * size.h / 2f
            LabelBox((a.x + b.x) / 2f + nx * push, (a.y + b.y) / 2f + ny * push, size.w, size.h)
        }
        val labels = LabelLayout.resolve(boxes, width, height)
        return PlanScreen(screen, labels, map(centroid(pts)), scale.toFloat())
    }

    /**
     * Unit normal pointing away from the interior, for a wall a -> b of a plan that is counter-clockwise in plan
     * space (y up). Plan outward is the right-hand normal (dy, −dx); flipping y for the screen gives (−dy, dx).
     */
    internal fun outwardNormal(a: ScreenPoint, b: ScreenPoint): Pair<Float, Float> {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) return 0f to -1f
        return -dy / len to dx / len
    }

    /** Area-weighted polygon centroid; bounding-box centre for degenerate input. */
    fun centroid(p: List<Vec2>): Vec2 {
        val a = PolygonMath.signedArea(p)
        if (p.size < 3 || abs(a) < 1e-9) {
            return Vec2((p.minOf { it.x } + p.maxOf { it.x }) / 2, (p.minOf { it.y } + p.maxOf { it.y }) / 2)
        }
        var cx = 0.0
        var cy = 0.0
        for (i in p.indices) {
            val q = p[i]
            val r = p[(i + 1) % p.size]
            val f = q cross r
            cx += (q.x + r.x) * f
            cy += (q.y + r.y) * f
        }
        return Vec2(cx / (6 * a), cy / (6 * a))
    }

    private const val EPS = 1e-9
}
