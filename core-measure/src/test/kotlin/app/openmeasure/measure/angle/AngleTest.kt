package app.openmeasure.measure.angle

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleTest {
    private val o = Vec3(0f, 0f, 0f)
    private val good = EndpointObservation(HitSource.DEPTH, 0.95f, 1f)
    private val medium = EndpointObservation(HitSource.DEPTH, 0.5f, 1f)

    @Test fun rightAngle() = assertEquals(90.0, AngleMath.angleDegrees(Vec3(1f, 0f, 0f), o, Vec3(0f, 0f, 1f))!!, 1e-4)

    @Test fun straightAndZero() {
        assertEquals(180.0, AngleMath.angleDegrees(Vec3(1f, 0f, 0f), o, Vec3(-2f, 0f, 0f))!!, 1e-4)
        assertEquals(0.0, AngleMath.angleDegrees(Vec3(1f, 0f, 0f), o, Vec3(3f, 0f, 0f))!!, 1e-4)
    }

    @Test fun sixtyDegreesAndSupplementary() {
        val d = AngleMath.angleDegrees(Vec3(1f, 0f, 0f), o, Vec3(0.5f, 0.8660254f, 0f))!!
        assertEquals(60.0, d, 1e-3)
        assertEquals(120.0, AngleMath.supplementary(d), 1e-3)
    }

    @Test fun degenerateIsNull() {
        assertNull(AngleMath.angleDegrees(o, o, Vec3(1f, 0f, 0f)))
        assertNull(AngleMath.angleDegrees(Vec3(1f, 0f, 0f), o, o))
        assertNull(AngleMath.angleDegrees(Vec3(Float.NaN, 0f, 0f), o, Vec3(1f, 0f, 0f)))
    }

    @Test fun horizontalAndVertical() {
        assertEquals(45.0, AngleMath.angleToHorizontalDegrees(o, Vec3(1f, 1f, 0f))!!, 1e-3)
        assertEquals(0.0, AngleMath.angleToHorizontalDegrees(o, Vec3(1f, 0f, 1f))!!, 1e-3)
        assertEquals(0.0, AngleMath.angleToVerticalDegrees(o, Vec3(0f, -2f, 0f))!!, 1e-3)
        assertNull(AngleMath.angleToHorizontalDegrees(o, o))
    }

    @Test fun labelShowsAngleWithWeakestLevel() {
        val l = AngleLabel.build(
            TrackingStatus.TRACKING, good, medium, good, Vec3(1f, 0f, 0f), o, Vec3(0f, 0f, 1f),
        ) as AngleContent.Angle
        assertEquals(90.0, l.degrees, 1e-4)
        assertEquals(ConfidenceLevel.MEDIUM, l.level)
    }

    @Test fun labelNeverNumberWhenTrackingLost() {
        val l = AngleLabel.build(TrackingStatus.PAUSED, good, good, good, Vec3(1f, 0f, 0f), o, Vec3(0f, 0f, 1f))
        assertTrue(l is AngleContent.Hint)
        val none = AngleLabel.build(
            TrackingStatus.TRACKING, good, EndpointObservation.NONE, good, Vec3(1f, 0f, 0f), o, Vec3(0f, 0f, 1f),
        )
        assertTrue(none is AngleContent.Hint)
    }

    @Test fun shortArmIsNull() {
        assertNull(AngleLabel.build(TrackingStatus.TRACKING, good, good, good, Vec3(0.01f, 0f, 0f), o, Vec3(0f, 0f, 1f)))
        assertNotNull(AngleLabel.build(TrackingStatus.TRACKING, good, good, good, Vec3(0.03f, 0f, 0f), o, Vec3(0f, 0f, 1f)))
    }

    @Test fun formatAndSpeak() {
        assertEquals("72.5°", AngleLabel.format(72.46))
        assertEquals("90.0°", AngleLabel.format(90.0))
        assertEquals("Angle, 90 degrees", AngleLabel.speak(90.0))
        assertEquals("Angle, 72.5 degrees", AngleLabel.speak(72.46))
        assertNull(AngleLabel.speak(Double.NaN))
    }

    @Test fun arcPicksSmallerSweep() {
        val s = AngleArc.compute(0f, 0f, 10f, 0f, 0f, 10f)!!
        assertEquals(90f, s.sweepDeg, 1e-3f)
        // Across the +-180 wrap: arms at 170 and -170 degrees sweep 20, not 340.
        val w = AngleArc.compute(0f, 0f, -10f, 1.7632698f, -10f, -1.7632698f)!!
        assertEquals(20f, Math.abs(w.sweepDeg), 0.05f)
        assertTrue(w.bisectorX < -0.9f)
        assertNull(AngleArc.compute(1f, 1f, 1f, 1f, 5f, 5f))
    }
}
