package app.openmeasure.measure.rect

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.label.LabelContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RectLabelTest {
    private val rect = QuadAnalyzer().analyze(
        listOf(Vec3(0f, 0f, 0f), Vec3(0.6f, 0f, 0f), Vec3(0.6f, 0f, -0.4f), Vec3(0f, 0f, -0.4f)),
    )
    private val good = EndpointObservation(HitSource.DEPTH, 0.95f, 1f)
    private val medium = EndpointObservation(HitSource.DEPTH, 0.5f, 1f)

    @Test
    fun highConfidenceShowsAreaAndSides() {
        val l = RectLabel.build(TrackingStatus.TRACKING, List(4) { good }, rect)
        val area = l.area as AreaContent.Area
        assertEquals(0.24, area.squareMeters, 1e-4)
        assertEquals(ConfidenceLevel.HIGH, area.level)
        assertTrue(area.isRectangle)
        assertFalse(area.notPlanar)
        assertEquals(0.6, (l.sides[0] as LabelContent.Length).meters, 1e-4)
        assertEquals(0.4, (l.sides[1] as LabelContent.Length).meters, 1e-4)
    }

    @Test
    fun areaTakesWeakestCorner() {
        val l = RectLabel.build(TrackingStatus.TRACKING, listOf(good, good, medium, good), rect)
        assertEquals(ConfidenceLevel.MEDIUM, (l.area as AreaContent.Area).level)
        assertEquals(ConfidenceLevel.HIGH, (l.sides[0] as LabelContent.Length).level)
        assertEquals(ConfidenceLevel.MEDIUM, (l.sides[1] as LabelContent.Length).level)
    }

    @Test
    fun noNumberWhenTrackingPoorOrCornerMissing() {
        val lost = RectLabel.build(TrackingStatus.PAUSED, List(4) { good }, rect)
        assertEquals(AreaContent.Hint(ConfidenceReason.TRACKING_POOR), lost.area)
        assertTrue(lost.sides.all { it is LabelContent.Hint })
        val missing = RectLabel.build(TrackingStatus.TRACKING, listOf(good, EndpointObservation.NONE, good, good), rect)
        assertEquals(AreaContent.Hint(ConfidenceReason.NO_HIT), missing.area)
        assertTrue(missing.sides[2] is LabelContent.Length)
    }

    @Test
    fun degenerateQuadShowsNoArea() {
        val bowTie = QuadAnalyzer().analyze(
            listOf(Vec3(0f, 0f, 0f), Vec3(0.6f, 0f, -0.4f), Vec3(0.6f, 0f, 0f), Vec3(0f, 0f, -0.4f)),
        )
        val l = RectLabel.build(TrackingStatus.TRACKING, List(4) { good }, bowTie)
        assertEquals(AreaContent.Invalid, l.area)
        assertTrue(l.sides.all { it is LabelContent.Hint })
    }

    @Test
    fun notPlanarCapsAtLow() {
        val bent = QuadAnalyzer().analyze(
            listOf(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(1f, 0.1f, -1f), Vec3(0f, 0f, -1f)),
        )
        val area = RectLabel.build(TrackingStatus.TRACKING, List(4) { good }, bent).area as AreaContent.Area
        assertTrue(area.notPlanar)
        assertEquals(ConfidenceLevel.LOW, area.level)
    }
}
