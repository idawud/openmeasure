package app.openmeasure.measure.box

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxFitTest {
    private val eps = 1e-4

    /** Rotates (x, z) by [deg] about +Y, then translates. */
    private fun p(x: Float, y: Float, z: Float, deg: Double = 0.0, off: Vec3 = Vec3.ZERO): Vec3 {
        val r = Math.toRadians(deg)
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return Vec3(x * c - z * s, y, x * s + z * c) + off
    }

    @Test fun alignedBox() {
        val box = BoxFit.fit(p(0f, 0f, 0f), p(0.6f, 0f, 0f), p(0.6f, 0f, 0.4f), p(0.3f, 0.25f, 0.2f))
        assertEquals(0.6, box.width, eps)
        assertEquals(0.4, box.depth, eps)
        assertEquals(0.25, box.height, eps)
        assertEquals(0.06, box.volume, eps)
        assertTrue(box.isValid)
        assertTrue(box.warnings.isEmpty())
        assertEquals(8, box.corners.size)
        assertEquals(Vec3(0.6f, 0.25f, 0.4f).distanceTo(box.corners[6]), 0f, 1e-4f)
    }

    @Test fun cornersFollowSideOfC() {
        val box = BoxFit.fit(p(0f, 0f, 0f), p(1f, 0f, 0f), p(0.2f, 0f, -0.5f), p(0f, 0.3f, 0f))
        assertEquals(0.5, box.depth, eps)
        assertEquals(-0.5f, box.corners[3].z, 1e-4f)
        assertEquals(0f, box.corners[3].x, 1e-4f)
    }

    @Test fun cAnywhereOnAdjacentEdgeAndTopAnywhereOnFace() {
        // C mid-way along the depth edge, and slightly past B along the width direction: only its perpendicular counts.
        val box = BoxFit.fit(p(0f, 0f, 0f), p(0.5f, 0f, 0f), p(0.55f, 0f, 0.15f), p(0.1f, 0.4f, 0.05f))
        assertEquals(0.15, box.depth, eps)
        assertEquals(0.4, box.height, eps)
    }

    @Test fun rotatedAboutYAndOffset() {
        val off = Vec3(2f, -1.2f, -3f)
        for (deg in listOf(17.0, 90.0, 135.0, 260.0)) {
            val box = BoxFit.fit(
                p(0f, 0f, 0f, deg, off), p(0.6f, 0f, 0f, deg, off), p(0.6f, 0f, 0.4f, deg, off), p(0.2f, 0.25f, 0.3f, deg, off),
            )
            assertEquals(0.6, box.width, eps)
            assertEquals(0.4, box.depth, eps)
            assertEquals(0.25, box.height, eps)
            assertEquals(0.06, box.volume, eps)
        }
    }

    @Test fun pointOrderReversedKeepsDims() {
        val box = BoxFit.fit(p(0.6f, 0f, 0f), p(0f, 0f, 0f), p(0f, 0f, 0.4f), p(0.3f, 0.25f, 0.2f))
        assertEquals(0.6, box.width, eps)
        assertEquals(0.4, box.depth, eps)
    }

    @Test fun smallBaseNoiseIsAveraged() {
        val box = BoxFit.fit(p(0f, 0.01f, 0f), p(0.6f, -0.01f, 0f), p(0.6f, 0f, 0.4f), p(0.3f, 0.25f, 0.2f))
        assertFalse(BoxWarning.BASE_NOT_LEVEL in box.warnings)
        assertEquals(0.25, box.height, eps)
        assertEquals(0.6, box.width, eps) // horizontal only
    }

    @Test fun offLevelBaseWarns() {
        val box = BoxFit.fit(p(0f, 0f, 0f), p(0.6f, 0.05f, 0f), p(0.6f, 0f, 0.4f), p(0.3f, 0.3f, 0.2f))
        assertTrue(BoxWarning.BASE_NOT_LEVEL in box.warnings)
        assertTrue(box.isValid)
    }

    @Test fun levelToleranceScalesWithLargeBase() {
        // 4 cm spread on a 2 m base is within 3 % (6 cm).
        val box = BoxFit.fit(p(0f, 0f, 0f), p(2f, 0.04f, 0f), p(2f, 0f, 1f), p(1f, 1f, 0.5f))
        assertFalse(BoxWarning.BASE_NOT_LEVEL in box.warnings)
    }

    @Test fun tinyDimsAreDegenerate() {
        val narrow = BoxFit.fit(p(0f, 0f, 0f), p(0.6f, 0f, 0f), p(0.6f, 0f, 0.005f), p(0.3f, 0.2f, 0f))
        assertTrue(BoxWarning.DEGENERATE in narrow.warnings)
        assertEquals(0.0, narrow.volume, 0.0)
        val vertical = BoxFit.fitBase(p(0f, 0f, 0f), p(0f, 0.5f, 0f), p(0.3f, 0f, 0f))
        assertTrue(BoxWarning.DEGENERATE in vertical.warnings)
        val flat = BoxFit.fit(p(0f, 0f, 0f), p(0.6f, 0f, 0f), p(0.6f, 0f, 0.4f), p(0.3f, 0.005f, 0.2f))
        assertTrue(BoxWarning.DEGENERATE in flat.warnings)
    }

    @Test fun topBelowBaseIsDegenerate() {
        val box = BoxFit.fit(p(0f, 0f, 0f), p(0.6f, 0f, 0f), p(0.6f, 0f, 0.4f), p(0.3f, -0.2f, 0.2f))
        assertFalse(box.isValid)
        assertEquals(0.0, box.height, 0.0)
        assertEquals(0.0, box.volume, 0.0)
    }

    @Test fun twelveDistinctEdges() {
        assertEquals(12, BoxFit.EDGES.size)
        assertEquals(12, BoxFit.EDGES.map { (a, b) -> minOf(a, b) to maxOf(a, b) }.toSet().size)
        // Every corner has degree 3 in a cuboid wireframe.
        val degree = IntArray(8)
        BoxFit.EDGES.forEach { (a, b) -> degree[a]++; degree[b]++ }
        assertTrue(degree.all { it == 3 })
    }
}
