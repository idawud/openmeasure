package app.openmeasure.measure.box

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxLabelTest {
    private val a = Vec3(0f, 0f, 0f)
    private val b = Vec3(0.6f, 0f, 0f)
    private val c = Vec3(0.6f, 0f, 0.4f)
    private val t = Vec3(0.3f, 0.25f, 0.2f)
    private val good = EndpointObservation(HitSource.DEPTH, 0.95f, 1f)
    private val medium = EndpointObservation(HitSource.DEPTH, 0.5f, 1f)
    private val tracking = TrackingStatus.TRACKING

    @Test fun allHighShowsVolume() {
        val base = BoxFit.fitBase(a, b, c)
        val l = BoxLabel.build(tracking, List(4) { good }, base, BoxFit.fit(base, t))
        val v = l.volume as VolumeContent.Volume
        assertEquals(0.06, v.cubicMeters, 1e-4)
        assertEquals(ConfidenceLevel.HIGH, v.level)
        assertEquals(0.25, (l.height as LabelContent.Length).meters, 1e-4)
    }

    @Test fun weakestPointGatesDependentLabels() {
        val base = BoxFit.fitBase(a, b, c)
        val l = BoxLabel.build(tracking, listOf(good, good, good, medium), base, BoxFit.fit(base, t))
        assertEquals(ConfidenceLevel.HIGH, (l.width as LabelContent.Length).level)
        assertEquals(ConfidenceLevel.HIGH, (l.depth as LabelContent.Length).level)
        assertEquals(ConfidenceLevel.MEDIUM, (l.height as LabelContent.Length).level)
        assertEquals(ConfidenceLevel.MEDIUM, (l.volume as VolumeContent.Volume).level)
    }

    @Test fun baseOnlyHasNoVolume() {
        val l = BoxLabel.build(tracking, List(3) { good }, BoxFit.fitBase(a, b, c), null)
        assertNull(l.height)
        assertNull(l.volume)
        assertEquals(0.4, (l.depth as LabelContent.Length).meters, 1e-4)
    }

    @Test fun poorTrackingNeverShowsNumber() {
        val base = BoxFit.fitBase(a, b, c)
        val l = BoxLabel.build(TrackingStatus.PAUSED, List(4) { good }, base, BoxFit.fit(base, t))
        assertEquals(VolumeContent.Hint(ConfidenceReason.TRACKING_POOR), l.volume)
        assertTrue(l.width is LabelContent.Hint)
    }

    @Test fun missingHitIsHint() {
        val base = BoxFit.fitBase(a, b, c)
        val l = BoxLabel.build(tracking, listOf(good, good, good, EndpointObservation.NONE), base, BoxFit.fit(base, t))
        assertEquals(VolumeContent.Hint(ConfidenceReason.NO_HIT), l.volume)
        assertTrue(l.depth is LabelContent.Length)
    }

    @Test fun degenerateIsInvalid() {
        val base = BoxFit.fitBase(a, b, c)
        val l = BoxLabel.build(tracking, List(4) { good }, base, BoxFit.fit(base, Vec3(0f, -0.1f, 0f)))
        assertEquals(VolumeContent.Invalid, l.volume)
    }

    @Test fun offLevelCapsAtLow() {
        val base = BoxFit.fitBase(a, Vec3(0.6f, 0.06f, 0f), c)
        val l = BoxLabel.build(tracking, List(4) { good }, base, BoxFit.fit(base, t))
        val v = l.volume as VolumeContent.Volume
        assertTrue(v.baseNotLevel)
        assertEquals(ConfidenceLevel.LOW, v.level)
        assertEquals(ConfidenceLevel.LOW, (l.width as LabelContent.Length).level)
    }

    @Test fun speak() {
        assertEquals("Volume, 0.06 cubic metres, 60 litres", BoxLabel.speak(0.06, UnitSystem.METRIC))
        assertNull(BoxLabel.speak(Double.NaN, UnitSystem.METRIC))
    }
}
