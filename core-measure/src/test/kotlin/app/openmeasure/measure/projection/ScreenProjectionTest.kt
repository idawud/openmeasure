package app.openmeasure.measure.projection

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenProjectionTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Simple symmetric perspective, f=1, looking down -z (column-major). */
    private val proj = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, -1.002f, -1f,
        0f, 0f, -0.2f, 0f,
    )

    @Test fun centreProjectsToCentre() {
        val p = ScreenProjection.project(Vec3(0f, 0f, -2f), identity, proj, 1000f, 500f)!!
        assertEquals(500f, p.x, 0.01f); assertEquals(250f, p.y, 0.01f)
    }

    @Test fun upIsSmallerYAndRightIsLargerX() {
        val p = ScreenProjection.project(Vec3(1f, 1f, -2f), identity, proj, 1000f, 500f)!!
        assertEquals(750f, p.x, 0.01f); assertEquals(125f, p.y, 0.01f)
    }

    @Test fun viewTranslationApplied() {
        val view = identity.copyOf().also { it[12] = -1f } // camera moved +1 x
        val p = ScreenProjection.project(Vec3(1f, 0f, -2f), view, proj, 1000f, 500f)!!
        assertEquals(500f, p.x, 0.01f)
    }

    @Test fun behindCameraIsNull() {
        assertNull(ScreenProjection.project(Vec3(0f, 0f, 2f), identity, proj, 1000f, 500f))
    }

    @Test fun segmentWithOneEndBehindIsClipped() {
        val s = ScreenProjection.projectSegment(Vec3(0f, 0f, -2f), Vec3(0f, 0f, 5f), identity, proj, 1000f, 500f)
        assertNotNull(s)
        assertEquals(500f, s!!.a.x, 0.01f)
        assertTrue(s.b.x.isFinite() && s.b.y.isFinite())
    }

    @Test fun segmentFullyBehindIsNull() {
        assertNull(ScreenProjection.projectSegment(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 3f), identity, proj, 1000f, 500f))
    }

    @Test fun labelLiftedAndClamped() {
        val seg = ScreenSegment(ScreenPoint(100f, 200f), ScreenPoint(300f, 200f))
        val p = LabelPlacement.place(seg, 1000f, 500f, 50f, 20f, 30f)
        assertEquals(200f, p.x, 0.01f); assertEquals(170f, p.y, 0.01f)
        val edge = ScreenSegment(ScreenPoint(-100f, 5f), ScreenPoint(-50f, 5f))
        val q = LabelPlacement.place(edge, 1000f, 500f, 50f, 20f, 30f)
        assertEquals(50f, q.x, 0.01f); assertEquals(20f, q.y, 0.01f)
    }
}
