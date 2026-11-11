package app.openmeasure.measure.room

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.atan2

enum class RoomWarning {
    /** Fewer than 3 corners: no floor polygon yet. */
    TOO_FEW_CORNERS,
    /** A wall shorter than [RoomPlan.MIN_WALL_M] or zero area (duplicate / collinear taps). */
    DEGENERATE,
    /** Walls cross or touch (taps out of order); area and angles are meaningless. */
    SELF_INTERSECTING,
    /** Corner heights spread more than max(5 cm, 3 % of the room extent): floor not level or tracking drifted. */
    UNEVEN_FLOOR,
    /** Ceiling not tapped yet: no height or volume. */
    NO_CEILING,
    /** Ceiling tap is less than [RoomPlan.MIN_CEILING_M] above the floor (tapped the floor or a low object). */
    CEILING_TOO_LOW,
}

/**
 * F15 floor plan from the tapped floor corners.
 *
 * Plan space is a top view in metres: world (x, −z) (Y up, so this keeps the true handedness — counter-clockwise
 * in the plan is counter-clockwise seen from above), translated so the first tapped corner is the origin and rotated
 * so the longest wall runs along +x. Corners are always counter-clockwise; [sourceIndex] maps each plan corner back to
 * the tap index (the order is reversed when the user walked clockwise). Wall i runs corners[i] -> corners[i + 1].
 * Lengths are horizontal (projected on the floor plane at [floorY], the mean corner height).
 */
data class RoomPlan(
    val corners: List<Vec2>,
    val sourceIndex: List<Int>,
    val floorY: Double,
    val wallLengths: List<Double>,
    val interiorAnglesDeg: List<Double>,
    val areaM2: Double,
    val perimeterM: Double,
    /** Every corner within [ORTHO_TOL_DEG] of 90° or 270° (box or L-shaped rooms); false = irregular. */
    val isRectilinear: Boolean,
    val ceilingHeightM: Double?,
    val warnings: Set<RoomWarning>,
) {
    /** A closed, simple polygon with a usable area (UNEVEN_FLOOR / ceiling warnings do not invalidate). */
    val isValid: Boolean get() = warnings.none { it in INVALIDATING }
    val wallCount: Int get() = corners.size
    val volumeM3: Double? get() = ceilingHeightM?.takeIf { isValid }?.let { it * areaM2 }

    /** Wall i endpoints as tap indices (for per-wall confidence). */
    fun wallSources(i: Int): Pair<Int, Int> = sourceIndex[i] to sourceIndex[(i + 1) % sourceIndex.size]

    /** Same plan with a ceiling height from tap [t] (any point on the ceiling, e.g. above a wall). */
    fun withCeiling(t: Vec3?): RoomPlan = withCeilingHeight(t?.let { it.y - floorY })

    /** Same plan with a known ceiling height in metres (e.g. restored from history); null = not measured. */
    fun withCeilingHeight(h: Double?): RoomPlan {
        val base = warnings - RoomWarning.NO_CEILING - RoomWarning.CEILING_TOO_LOW
        if (h == null) return copy(ceilingHeightM = null, warnings = base + RoomWarning.NO_CEILING)
        if (!h.isFinite() || h < MIN_CEILING_M) return copy(ceilingHeightM = null, warnings = base + RoomWarning.CEILING_TOO_LOW)
        return copy(ceilingHeightM = h, warnings = base)
    }

    companion object {
        const val MIN_WALL_M = 0.05
        const val MIN_AREA_M2 = 0.05
        const val ORTHO_TOL_DEG = 5.0
        const val UNEVEN_MIN_M = 0.05
        const val UNEVEN_FRACTION = 0.03
        const val MIN_CEILING_M = 0.5
        private val INVALIDATING = setOf(RoomWarning.TOO_FEW_CORNERS, RoomWarning.DEGENERATE, RoomWarning.SELF_INTERSECTING)

        /** Builds the plan from world-space floor corners in tap order, plus an optional ceiling tap. */
        fun fromCorners(world: List<Vec3>, ceiling: Vec3? = null): RoomPlan {
            val n = world.size
            val warnings = mutableSetOf<RoomWarning>()
            val floorY = if (n == 0) 0.0 else world.sumOf { it.y.toDouble() } / n
            val raw = world.map { Vec2(it.x.toDouble(), -it.z.toDouble()) }
            if (n < 3) warnings += RoomWarning.TOO_FEW_CORNERS

            // Orientation: counter-clockwise, keeping the first tap first.
            val ccw = n < 3 || PolygonMath.signedArea(raw) >= 0.0
            val order = if (ccw) (0 until n).toList() else listOf(0) + (n - 1 downTo 1)
            val ordered = order.map { raw[it] }

            // Longest wall horizontal, first corner at the origin.
            val walls = ordered.indices.map { ordered[it].distanceTo(ordered[(it + 1) % n]) }
            val longest = walls.indices.maxByOrNull { walls[it] }
            val rot = longest?.let {
                val d = ordered[(it + 1) % n] - ordered[it]
                -atan2(d.y, d.x)
            } ?: 0.0
            val origin = ordered.firstOrNull() ?: Vec2(0.0, 0.0)
            val corners = ordered.map { (it - origin).rotated(rot) }

            val wallLengths = if (n < 2) emptyList() else if (n == 2) listOf(walls[0]) else walls
            val area = if (n < 3) 0.0 else abs(PolygonMath.signedArea(corners))
            if (n >= 3) {
                if (walls.any { it < MIN_WALL_M } || area < MIN_AREA_M2) warnings += RoomWarning.DEGENERATE
                if (PolygonMath.isSelfIntersecting(corners)) warnings += RoomWarning.SELF_INTERSECTING
            }
            val angles = if (n < 3) emptyList() else PolygonMath.interiorAnglesDeg(corners)
            val rectilinear = angles.isNotEmpty() && warnings.none { it in INVALIDATING } && angles.all { abs(it - 90.0) <= ORTHO_TOL_DEG || abs(it - 270.0) <= ORTHO_TOL_DEG }

            if (n >= 2) {
                val spread = world.maxOf { it.y } - world.minOf { it.y }
                val extent = raw.maxOf { a -> raw.maxOf { b -> a.distanceTo(b) } }
                if (spread > maxOf(UNEVEN_MIN_M, UNEVEN_FRACTION * extent)) warnings += RoomWarning.UNEVEN_FLOOR
            }
            val plan = RoomPlan(
                corners, order, floorY, wallLengths, angles, area,
                if (n < 3) wallLengths.sum() else PolygonMath.perimeter(corners), rectilinear, null, warnings,
            )
            return plan.withCeiling(ceiling)
        }
    }
}
