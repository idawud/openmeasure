package app.openmeasure.measure.box

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Problems with a fitted box. A box with [DEGENERATE] has no meaningful volume. */
enum class BoxWarning {
    /** A dimension is below [BoxFit.MIN_DIM_M], or the top point is not above the base. */
    DEGENERATE,
    /** The three base points differ in height by more than [BoxFit.LEVEL_TOL_M] / [BoxFit.LEVEL_TOL_FRACTION]. */
    BASE_NOT_LEVEL,
}

/**
 * Base rectangle from steps 1–2. [corners] = A', B', B'+d, A'+d on the horizontal plane y = [baseY]
 * (A', B' are A, B dropped onto that plane). [depth] is |d|; [depthSigned] keeps the side of AB that C lies on.
 */
data class BoxBase(
    val width: Double,
    val depth: Double,
    val depthSigned: Double,
    val baseY: Float,
    val corners: List<Vec3>,
    val warnings: Set<BoxWarning>,
)

/**
 * Full box. [corners] has 8 entries: base 0..3 (A', B', B'+d, A'+d), then top 4..7 directly above them.
 * Edges: base i–(i+1)%4, top 4+i–4+(i+1)%4, verticals i–4+i (see [BoxFit.EDGES]).
 */
data class BoxResult(
    val width: Double,
    val depth: Double,
    val height: Double,
    val volume: Double,
    val corners: List<Vec3>,
    val warnings: Set<BoxWarning>,
) {
    val isValid: Boolean get() = BoxWarning.DEGENERATE !in warnings
}

/**
 * F14 box fit from guided taps, assuming a cuboid resting on a level surface (edges aligned with gravity, world +Y up):
 * 1. A, B along one bottom edge -> width = horizontal |AB|.
 * 2. C anywhere on the adjacent bottom edge -> depth = signed horizontal distance of C from line AB.
 * 3. T anywhere on the top face -> height = T.y - mean(A.y, B.y, C.y).
 *
 * Only horizontal components are used for W/D, so small vertical tap noise on the base does not inflate them;
 * height uses only Y, so T need not be a corner. Dimension labels stay W/D/H as tapped (not sorted).
 */
object BoxFit {
    const val MIN_DIM_M = 0.01
    const val LEVEL_TOL_M = 0.03f
    const val LEVEL_TOL_FRACTION = 0.03f

    /** 12 wireframe edges as index pairs into [BoxResult.corners]. */
    val EDGES: List<Pair<Int, Int>> = (0..3).flatMap { i ->
        listOf(i to (i + 1) % 4, 4 + i to 4 + (i + 1) % 4, i to 4 + i)
    }

    fun fitBase(a: Vec3, b: Vec3, c: Vec3): BoxBase {
        val baseY = (a.y + b.y + c.y) / 3f
        val ux = (b.x - a.x).toDouble()
        val uz = (b.z - a.z).toDouble()
        val width = sqrt(ux * ux + uz * uz)
        val warnings = HashSet<BoxWarning>()
        // Horizontal perpendicular of AB (rotate 90° about +Y); zero if AB is (nearly) vertical.
        val (px, pz) = if (width > 1e-9) Pair(-uz / width, ux / width) else Pair(0.0, 0.0)
        val depthSigned = (c.x - a.x) * px + (c.z - a.z) * pz
        val depth = abs(depthSigned)
        if (!width.isFinite() || width < MIN_DIM_M || !depth.isFinite() || depth < MIN_DIM_M) warnings += BoxWarning.DEGENERATE
        val spread = maxOf(a.y, b.y, c.y) - minOf(a.y, b.y, c.y)
        // Lenient on purpose: 3 cm, or 3 % of the larger base side for big boxes (depth noise grows with size/range).
        val tol = max(LEVEL_TOL_M, LEVEL_TOL_FRACTION * max(width, depth).toFloat())
        if (spread > tol) warnings += BoxWarning.BASE_NOT_LEVEL
        val a0 = Vec3(a.x, baseY, a.z)
        val b0 = Vec3(b.x, baseY, b.z)
        val d = Vec3((px * depthSigned).toFloat(), 0f, (pz * depthSigned).toFloat())
        return BoxBase(width, depth, depthSigned, baseY, listOf(a0, b0, b0 + d, a0 + d), warnings)
    }

    fun fit(a: Vec3, b: Vec3, c: Vec3, top: Vec3): BoxResult = fit(fitBase(a, b, c), top)

    fun fit(base: BoxBase, top: Vec3): BoxResult {
        val h = (top.y - base.baseY).toDouble()
        val warnings = base.warnings.toMutableSet()
        if (!h.isFinite() || h < MIN_DIM_M) warnings += BoxWarning.DEGENERATE
        val lift = Vec3(0f, h.toFloat(), 0f)
        val corners = base.corners + base.corners.map { it + lift }
        val height = if (h.isFinite()) max(h, 0.0) else 0.0
        val valid = BoxWarning.DEGENERATE !in warnings
        val volume = if (valid) base.width * base.depth * height else 0.0
        return BoxResult(base.width, base.depth, height, volume, corners, warnings)
    }
}
