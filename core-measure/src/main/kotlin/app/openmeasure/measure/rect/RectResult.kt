package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Vec3

enum class RectWarning {
    /** A corner is farther from the best-fit plane than the planarity tolerance (numbers still computed). */
    NOT_PLANAR,
    /** Valid convex quadrilateral, but some interior angle is more than the tolerance away from 90 degrees. */
    NOT_RECTANGULAR,
    /** Coincident/collinear corners, a tiny side or angle, self-intersecting (bow-tie) or concave: no numbers. */
    DEGENERATE,
}

/**
 * Analysis of four corners (F9). All lengths in metres, areas in square metres, angles in degrees.
 *
 * Side i runs corners[i] -> corners[(i + 1) % 4]; diagonals are 0-2 and 1-3; anglesDeg[i] is the interior
 * angle at corners[i]. When [isValid] is false every number is NaN and nothing may be displayed.
 */
data class RectResult(
    /** Input corners projected onto the best-fit plane, counter-clockwise seen from [normal]'s side. */
    val corners: List<Vec3>,
    /** corners[i] is the projection of input[order[i]]. */
    val order: List<Int>,
    /** Unit normal of the best-fit plane, or null if no plane could be fitted. */
    val normal: Vec3?,
    val sidesM: List<Double>,
    val diagonalsM: List<Double>,
    val anglesDeg: List<Double>,
    /** Shoelace area of the projected quadrilateral (exact for any convex quad). */
    val areaM2: Double,
    val isRectangle: Boolean,
    /** Mean of the longer pair of opposite sides; NaN unless [isRectangle]. */
    val widthM: Double,
    /** Mean of the shorter pair of opposite sides; NaN unless [isRectangle]. */
    val heightM: Double,
    /** [widthM] x [heightM]; NaN unless [isRectangle]. Agrees with [areaM2] to within the angle tolerance. */
    val rectAreaM2: Double,
    /** Largest distance of an input corner from the best-fit plane. */
    val maxPlaneDeviationM: Double,
    val warnings: Set<RectWarning>,
) {
    val isValid: Boolean get() = RectWarning.DEGENERATE !in warnings

    companion object {
        private val NAN4 = List(4) { Double.NaN }

        fun degenerate(
            points: List<Vec3>,
            normal: Vec3? = null,
            deviation: Double = Double.NaN,
            notPlanar: Boolean = false,
        ) = RectResult(
            corners = points, order = points.indices.toList(), normal = normal,
            sidesM = NAN4, diagonalsM = listOf(Double.NaN, Double.NaN), anglesDeg = NAN4,
            areaM2 = Double.NaN, isRectangle = false, widthM = Double.NaN, heightM = Double.NaN,
            rectAreaM2 = Double.NaN, maxPlaneDeviationM = deviation,
            warnings = if (notPlanar) setOf(RectWarning.DEGENERATE, RectWarning.NOT_PLANAR) else setOf(RectWarning.DEGENERATE),
        )
    }
}
