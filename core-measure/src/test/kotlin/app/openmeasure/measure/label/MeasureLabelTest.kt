package app.openmeasure.measure.label

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.hit.HitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureLabelTest {
    private val good = EndpointObservation(HitSource.DEPTH, 0.95f, 1.0f)

    @Test fun goodTrackingShowsLength() {
        val c = MeasureLabel.build(TrackingStatus.TRACKING, good, good, 0.84)
        assertTrue(c is LabelContent.Length)
        assertEquals(ConfidenceLevel.HIGH, (c as LabelContent.Length).level)
    }

    @Test fun poorTrackingShowsHintOnly() {
        val c = MeasureLabel.build(TrackingStatus.PAUSED, good, good, 0.84)
        assertEquals(LabelContent.Hint(ConfidenceReason.TRACKING_POOR), c)
    }

    @Test fun missingEndHitShowsHint() {
        val c = MeasureLabel.build(TrackingStatus.TRACKING, good, EndpointObservation.NONE, 0.84)
        assertEquals(LabelContent.Hint(ConfidenceReason.NO_HIT), c)
    }

    @Test fun nonFiniteLengthNeverShown() {
        assertTrue(MeasureLabel.build(TrackingStatus.TRACKING, good, good, Double.NaN) is LabelContent.Hint)
    }
}
