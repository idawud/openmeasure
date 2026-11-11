package app.openmeasure.measure.guide

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class GuideTest {
    private val o = Vec3.ZERO
    private fun tilted(deg: Double) = Vec3(1f, (tan(Math.toRadians(deg))).toFloat(), 0f)

    @Test fun deviations() {
        assertEquals(0f, GuideMath.deviationFromHorizontal(o, Vec3(1f, 0f, 1f))!!, 1e-3f)
        assertEquals(90f, GuideMath.deviationFromVertical(o, Vec3(1f, 0f, 0f))!!, 1e-3f)
        assertEquals(0f, GuideMath.deviationFromVertical(o, Vec3(0f, -2f, 0f))!!, 1e-3f)
        assertEquals(10f, GuideMath.deviationFromHorizontal(o, tilted(10.0))!!, 1e-2f)
    }

    @Test fun tooShortIsNull() {
        assertNull(GuideMath.deviationFromHorizontal(o, Vec3(0.005f, 0f, 0f)))
    }

    @Test fun alignedEndKeepsTrueDistance() {
        val end = Vec3(1f, 0.02f, 1f)
        val h = GuideMath.alignedEnd(Vec3(0f, 1f, 0f), end, GuideState.HORIZONTAL)
        assertEquals(Vec3(1f, 1f, 1f), h)
        val v = GuideMath.alignedEnd(o, Vec3(0.01f, 2f, 0f), GuideState.VERTICAL)
        assertEquals(Vec3(0f, 2f, 0f), v)
        assertEquals(end, GuideMath.alignedEnd(o, end, GuideState.NONE))
    }

    @Test fun hysteresisAcquireHoldRelease() {
        val t = GuideTracker()
        assertEquals(GuideState.NONE, t.update(o, tilted(3.0)))
        assertEquals(GuideState.HORIZONTAL, t.update(o, tilted(1.5)))
        assertTrue(t.acquired)
        assertEquals(GuideState.HORIZONTAL, t.update(o, tilted(3.5)))
        assertFalse(t.acquired)
        assertEquals(GuideState.NONE, t.update(o, tilted(4.5)))
    }

    @Test fun verticalAndReset() {
        val t = GuideTracker()
        assertEquals(GuideState.VERTICAL, t.update(o, Vec3(0.01f, 1f, 0f)))
        assertEquals(GuideState.NONE, t.update(o, null))
        t.reset()
        assertEquals(GuideState.NONE, t.state)
    }
}
