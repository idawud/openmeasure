package app.openmeasure.measure.confidence

import app.openmeasure.measure.confidence.ConfidenceLevel.HIGH
import app.openmeasure.measure.confidence.ConfidenceLevel.LOW
import app.openmeasure.measure.confidence.ConfidenceLevel.MEDIUM
import app.openmeasure.measure.confidence.ConfidenceLevel.NONE
import app.openmeasure.measure.confidence.ConfidenceScorer.FAR_MIN_FACTOR
import app.openmeasure.measure.confidence.ConfidenceScorer.FAR_START_M
import app.openmeasure.measure.confidence.ConfidenceScorer.HIGH_MIN_SCORE
import app.openmeasure.measure.confidence.ConfidenceScorer.MAX_RANGE_M
import app.openmeasure.measure.confidence.ConfidenceScorer.MEDIUM_MIN_SCORE
import app.openmeasure.measure.confidence.ConfidenceScorer.MIN_DISPLAY_SCORE
import app.openmeasure.measure.confidence.ConfidenceScorer.PLANE_HIGH_MIN_CONFIDENCE
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.hit.HitSource.DEPTH
import app.openmeasure.measure.hit.HitSource.PLANE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceScorerTest {
    private fun ep(source: HitSource, conf: Float?, dist: Float = 1f) = EndpointObservation(source, conf, dist)

    private fun score(
        a: EndpointObservation,
        b: EndpointObservation = a,
        tracking: TrackingStatus = TrackingStatus.TRACKING,
    ) = ConfidenceScorer.score(ConfidenceInput(tracking, a, b))

    @Test fun pausedOrStoppedTrackingIsNone() {
        for (t in listOf(TrackingStatus.PAUSED, TrackingStatus.STOPPED)) {
            val r = score(ep(DEPTH, 1f), tracking = t)
            assertEquals(NONE, r.level)
            assertEquals(ConfidenceReason.TRACKING_POOR, r.reason)
            assertFalse(r.canDisplayNumber)
            assertEquals(0f, r.score, 0f)
        }
    }

    @Test fun trackingPoorTakesPriorityOverNoHit() {
        val r = score(EndpointObservation.NONE, tracking = TrackingStatus.PAUSED)
        assertEquals(ConfidenceReason.TRACKING_POOR, r.reason)
    }

    @Test fun anyEndpointNoneIsNone() {
        val r1 = score(ep(DEPTH, 1f), EndpointObservation.NONE)
        val r2 = score(EndpointObservation.NONE, ep(DEPTH, 1f))
        for (r in listOf(r1, r2)) {
            assertEquals(NONE, r.level)
            assertEquals(ConfidenceReason.NO_HIT, r.reason)
            assertFalse(r.canDisplayNumber)
        }
    }

    @Test fun confidentDepthCloseIsHighOk() {
        val r = score(ep(DEPTH, 0.95f, 1f))
        assertEquals(HIGH, r.level)
        assertEquals(ConfidenceReason.OK, r.reason)
        assertTrue(r.canDisplayNumber)
        assertEquals(0.95f, r.score, 1e-6f)
    }

    @Test fun levelThresholdBoundaries() {
        assertEquals(HIGH, ConfidenceScorer.levelFor(HIGH_MIN_SCORE))
        assertEquals(MEDIUM, ConfidenceScorer.levelFor(Math.nextDown(HIGH_MIN_SCORE)))
        assertEquals(MEDIUM, ConfidenceScorer.levelFor(MEDIUM_MIN_SCORE))
        assertEquals(LOW, ConfidenceScorer.levelFor(Math.nextDown(MEDIUM_MIN_SCORE)))
        assertEquals(LOW, ConfidenceScorer.levelFor(MIN_DISPLAY_SCORE))
        assertEquals(NONE, ConfidenceScorer.levelFor(Math.nextDown(MIN_DISPLAY_SCORE)))
    }

    @Test fun weakestEndpointDominates() {
        val r = score(ep(DEPTH, 0.95f), ep(DEPTH, 0.5f))
        assertEquals(MEDIUM, r.level)
        assertEquals(0.5f, r.score, 1e-6f)
        assertEquals(ConfidenceReason.LOW_DEPTH_CONFIDENCE, r.reason)
        assertEquals(r, score(ep(DEPTH, 0.5f), ep(DEPTH, 0.95f)))
    }

    @Test fun veryLowConfidenceHidesNumber() {
        val r = score(ep(DEPTH, 0.1f))
        assertEquals(NONE, r.level)
        assertFalse(r.canDisplayNumber)
        assertEquals(ConfidenceReason.LOW_DEPTH_CONFIDENCE, r.reason)
    }

    @Test fun lowConfidenceIsLowButDisplayable() {
        val r = score(ep(DEPTH, 0.3f))
        assertEquals(LOW, r.level)
        assertTrue(r.canDisplayNumber)
    }

    @Test fun distanceFactorBoundaries() {
        assertEquals(1f, ConfidenceScorer.distanceFactor(0f), 0f)
        assertEquals(1f, ConfidenceScorer.distanceFactor(FAR_START_M), 0f)
        assertTrue(ConfidenceScorer.distanceFactor(FAR_START_M + 0.001f) < 1f)
        assertEquals(FAR_MIN_FACTOR, ConfidenceScorer.distanceFactor(MAX_RANGE_M), 1e-6f)
        assertEquals((1f + FAR_MIN_FACTOR) / 2f, ConfidenceScorer.distanceFactor((FAR_START_M + MAX_RANGE_M) / 2f), 1e-6f)
    }

    @Test fun exactlyAtFarStartStaysHigh() {
        assertEquals(HIGH, score(ep(DEPTH, 1f, FAR_START_M)).level)
    }

    @Test fun justBeyondFarStartCapsAtMediumTooFar() {
        val r = score(ep(DEPTH, 1f, 1f), ep(DEPTH, 1f, FAR_START_M + 0.01f))
        assertEquals(MEDIUM, r.level)
        assertEquals(ConfidenceReason.TOO_FAR, r.reason)
        assertTrue(r.score > HIGH_MIN_SCORE) // raw score is not lowered by the cap
    }

    @Test fun beyondMaxRangeIsNoneTooFar() {
        assertEquals(LOW, score(ep(DEPTH, 0.5f, MAX_RANGE_M)).level)
        val r = score(ep(DEPTH, 1f, Math.nextUp(MAX_RANGE_M)))
        assertEquals(NONE, r.level)
        assertEquals(ConfidenceReason.TOO_FAR, r.reason)
        assertFalse(r.canDisplayNumber)
    }

    @Test fun nonFiniteDistanceIsNone() {
        assertEquals(NONE, score(ep(DEPTH, 1f, Float.NaN)).level)
        assertEquals(NONE, score(ep(DEPTH, 1f, Float.POSITIVE_INFINITY)).level)
    }

    @Test fun planeOnlyCapsAtMediumUnlessConfidenceHigh() {
        val capped = score(ep(PLANE, 0.85f))
        assertEquals(MEDIUM, capped.level)
        assertEquals(ConfidenceReason.PLANE_ONLY, capped.reason)
        assertEquals(HIGH, score(ep(PLANE, PLANE_HIGH_MIN_CONFIDENCE)).level)
        assertEquals(MEDIUM, score(ep(PLANE, Math.nextDown(PLANE_HIGH_MIN_CONFIDENCE))).level)
        // One weak plane endpoint caps a segment whose other end is strong depth.
        assertEquals(MEDIUM, score(ep(DEPTH, 1f), ep(PLANE, 0.8f)).level)
    }

    @Test fun planeWithLowScoreReportsLowConfidenceNotPlaneOnly() {
        val r = score(ep(PLANE, 0.5f))
        assertEquals(MEDIUM, r.level)
        assertEquals(ConfidenceReason.LOW_DEPTH_CONFIDENCE, r.reason)
    }

    @Test fun nullConfidenceUsesSourceDefaults() {
        val plane = score(ep(PLANE, null))
        assertEquals(ConfidenceScorer.DEFAULT_PLANE_CONFIDENCE, plane.score, 1e-6f)
        assertEquals(MEDIUM, plane.level)
        val depth = score(ep(DEPTH, null))
        assertEquals(ConfidenceScorer.DEFAULT_DEPTH_CONFIDENCE, depth.score, 1e-6f)
    }

    @Test fun outOfRangeConfidenceIsClamped() {
        assertEquals(1f, score(ep(DEPTH, 1.7f)).score, 0f)
        assertEquals(NONE, score(ep(DEPTH, -0.2f)).level)
        assertEquals(NONE, score(ep(DEPTH, Float.NaN)).level)
    }

    @Test fun hitResultOverloadMatchesObservation() {
        val a = HitResult(DEPTH, null, null, null, 0.9f, 1.2f)
        val b = HitResult(PLANE, null, null, null, 0.95f, 2.0f)
        val r = ConfidenceScorer.score(TrackingStatus.TRACKING, a, b)
        assertEquals(HIGH, r.level)
        assertEquals(0.9f, r.score, 1e-6f)
        assertEquals(NONE, ConfidenceScorer.score(TrackingStatus.TRACKING, a, HitResult.NONE).level)
    }
}
