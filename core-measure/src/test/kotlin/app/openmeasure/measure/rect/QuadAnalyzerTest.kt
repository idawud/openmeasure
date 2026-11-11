package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QuadAnalyzerTest {
    private val analyzer = QuadAnalyzer()
    private val eps = 1e-4

    private fun rectOnXZ(w: Float, h: Float) = listOf(
        Vec3(0f, 0f, 0f), Vec3(w, 0f, 0f), Vec3(w, 0f, -h), Vec3(0f, 0f, -h),
    )

    /** Rectangle w x h rotated by [rotDeg] in an arbitrarily tilted plane far from the origin. */
    private fun tiltedRect(w: Double, h: Double, rotDeg: Double): List<Vec3> {
        val basis = PlaneBasis.of(Vec3(2f, 1.5f, -3f), Vec3(0.3f, 1f, 0.5f), Vec3(1f, 0f, 0f))
        val a = Math.toRadians(rotDeg)
        val d = Vec2(cos(a), sin(a))
        val p = Vec2(-sin(a), cos(a))
        val c = Vec2(0.1, -0.2)
        return listOf(c, c + d * w, c + d * w + p * h, c + p * h).map { basis.to3d(it) }
    }

    @Test
    fun exactRectangle() {
        val r = analyzer.analyze(rectOnXZ(0.6f, 0.4f))
        assertTrue(r.isValid)
        assertTrue(r.isRectangle)
        assertTrue(r.warnings.isEmpty())
        assertEquals(0.24, r.areaM2, eps)
        assertEquals(0.24, r.rectAreaM2, eps)
        assertEquals(0.6, r.widthM, eps)
        assertEquals(0.4, r.heightM, eps)
        assertEquals(listOf(0.6, 0.4, 0.6, 0.4), r.sidesM.map { Math.round(it * 1e4) / 1e4 })
        r.anglesDeg.forEach { assertEquals(90.0, it, 1e-3) }
        assertEquals(sqrt(0.52), r.diagonalsM[0], eps)
        assertEquals(sqrt(0.52), r.diagonalsM[1], eps)
        assertEquals(0.0, r.maxPlaneDeviationM, 1e-6)
        assertEquals(1f, r.normal!!.length(), 1e-5f)
    }

    @Test
    fun rotatedRectangleInTiltedPlane() {
        val r = analyzer.analyze(tiltedRect(1.0, 0.5, 30.0))
        assertTrue(r.isRectangle)
        assertTrue(r.warnings.isEmpty())
        assertEquals(0.5, r.areaM2, 1e-4)
        assertEquals(1.0, r.widthM, 1e-4)
        assertEquals(0.5, r.heightM, 1e-4)
        assertTrue(r.maxPlaneDeviationM < 1e-5)
    }

    @Test
    fun clockwiseInputGivesPositiveArea() {
        val r = analyzer.analyze(rectOnXZ(0.6f, 0.4f).reversed())
        assertTrue(r.isRectangle)
        assertEquals(0.24, r.areaM2, eps)
    }

    @Test
    fun slightlySkewedStillRectangleWithinFiveDegrees() {
        // 3 degree shear: angles 87/93.
        val s = sin(Math.toRadians(3.0)).toFloat() * 0.4f
        val pts = listOf(Vec3(0f, 0f, 0f), Vec3(0.6f, 0f, 0f), Vec3(0.6f + s, 0f, -0.4f), Vec3(s, 0f, -0.4f))
        assertTrue(analyzer.analyze(pts).isRectangle)
    }

    @Test
    fun trapezoidIsNotRectangular() {
        // Parallel sides 1.0 and 0.6, height 0.5 -> area 0.4.
        val pts = listOf(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(0.8f, 0f, -0.5f), Vec3(0.2f, 0f, -0.5f))
        val r = analyzer.analyze(pts)
        assertTrue(r.isValid)
        assertFalse(r.isRectangle)
        assertEquals(setOf(RectWarning.NOT_RECTANGULAR), r.warnings)
        assertEquals(0.4, r.areaM2, eps)
        assertTrue(r.widthM.isNaN())
        assertEquals(360.0, r.anglesDeg.sum(), 1e-6)
    }

    @Test
    fun nonPlanarCornerIsFlaggedButStillMeasured() {
        val pts = rectOnXZ(1f, 1f).toMutableList()
        pts[2] = pts[2] + Vec3(0f, 0.10f, 0f) // 10 cm lift -> ~2.5 cm from the best-fit plane
        val r = analyzer.analyze(pts)
        assertTrue(RectWarning.NOT_PLANAR in r.warnings)
        assertTrue(r.isValid)
        assertEquals(0.025, r.maxPlaneDeviationM, 2e-3)
        assertTrue(r.areaM2 > 0.95 && r.areaM2 < 1.05)
    }

    @Test
    fun smallNoiseIsPlanar() {
        val pts = rectOnXZ(1f, 1f).toMutableList()
        pts[2] = pts[2] + Vec3(0f, 0.01f, 0f)
        assertFalse(RectWarning.NOT_PLANAR in analyzer.analyze(pts).warnings)
    }

    @Test
    fun smallObjectToleranceIsRelative() {
        // 10 cm card: 3% of the 14 cm diagonal = 4 mm, floored to 5 mm; 8 mm lift -> 2 mm deviation: fine.
        val ok = rectOnXZ(0.1f, 0.1f).toMutableList().also { it[2] = it[2] + Vec3(0f, 0.008f, 0f) }
        assertFalse(RectWarning.NOT_PLANAR in analyzer.analyze(ok).warnings)
        // 4 cm lift -> 1 cm off a 10 cm card: not planar even though < 2 cm.
        val bad = rectOnXZ(0.1f, 0.1f).toMutableList().also { it[2] = it[2] + Vec3(0f, 0.04f, 0f) }
        assertTrue(RectWarning.NOT_PLANAR in analyzer.analyze(bad).warnings)
    }

    @Test
    fun selfIntersectingOrderIsDegenerate() {
        val r = rectOnXZ(0.6f, 0.4f)
        val bowTie = listOf(r[0], r[2], r[1], r[3])
        val res = analyzer.analyze(bowTie)
        assertFalse(res.isValid)
        assertTrue(RectWarning.DEGENERATE in res.warnings)
        assertTrue(res.areaM2.isNaN())
    }

    @Test
    fun reorderFixesTapOrder() {
        val r = rectOnXZ(0.6f, 0.4f)
        val res = analyzer.analyze(listOf(r[0], r[2], r[1], r[3]), reorder = true)
        assertTrue(res.isRectangle)
        assertEquals(0.24, res.areaM2, eps)
        assertEquals(0, res.order[0])
        assertEquals(setOf(0, 1, 2, 3), res.order.toSet())
    }

    @Test
    fun concaveIsDegenerate() {
        val pts = listOf(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(0.3f, 0f, -0.3f), Vec3(0f, 0f, -1f))
        assertFalse(analyzer.analyze(pts).isValid)
    }

    @Test
    fun collinearAndCoincidentAreDegenerate() {
        val line = listOf(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(3f, 0f, 0f))
        assertFalse(analyzer.analyze(line).isValid)
        val same = List(4) { Vec3(1f, 1f, 1f) }
        val res = analyzer.analyze(same)
        assertFalse(res.isValid)
        assertNull(res.normal)
        // Three almost collinear corners (angle ~ 177 degrees).
        val flatTri = listOf(Vec3(0f, 0f, 0f), Vec3(0.5f, 0f, -0.01f), Vec3(1f, 0f, 0f), Vec3(0.5f, 0f, 0.5f))
        assertFalse(analyzer.analyze(flatTri).isValid)
    }

    @Test
    fun imperialSizedRectangleArea() {
        // 3 ft x 2 ft = 6 sq ft = 0.55741824 m^2.
        val r = analyzer.analyze(rectOnXZ(0.9144f, 0.6096f))
        assertEquals(0.55741824, r.areaM2, 1e-5)
        assertEquals("6.0 ft²", app.openmeasure.measure.units.AreaFormatter.format(r.areaM2, app.openmeasure.measure.units.UnitSystem.IMPERIAL))
    }

    @Test
    fun cornersAreProjectedOntoPlane() {
        val pts = rectOnXZ(1f, 1f).toMutableList()
        pts[2] = pts[2] + Vec3(0f, 0.01f, 0f)
        val r = analyzer.analyze(pts)
        assertNotNull(r.normal)
        val n = r.normal!!
        val d0 = r.corners[0] dot n
        r.corners.forEach { assertEquals(d0, it dot n, 1e-5f) }
    }
}
