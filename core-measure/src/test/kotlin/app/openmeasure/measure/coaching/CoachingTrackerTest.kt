package app.openmeasure.measure.coaching

import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachingTrackerTest {
    private val good = HitResult(HitSource.DEPTH, Vec3(0f, 0f, -1f), null, null, 0.9f, 1f)
    private val none = HitResult(HitSource.NONE, null, null, null, 0f, 0f)
    private val t = TrackingStatus.TRACKING

    @Test fun notTrackingIsMoveSlowly() {
        assertEquals(CoachingState.MOVE_SLOWLY, CoachingTracker().update(0, TrackingStatus.PAUSED, good))
    }

    @Test fun trackingNoHitIsScan() {
        assertEquals(CoachingState.SCAN_SURFACE, CoachingTracker().update(0, t, none))
    }

    @Test fun needsSustainedHitsToBeReady() {
        val c = CoachingTracker()
        assertEquals(CoachingState.SCAN_SURFACE, c.update(0, t, good))
        assertEquals(CoachingState.SCAN_SURFACE, c.update(200, t, good))
        assertEquals(CoachingState.READY, c.update(300, t, good))
    }

    @Test fun flickerResetsReadyTimer() {
        val c = CoachingTracker()
        c.update(0, t, good); c.update(200, t, none)
        assertEquals(CoachingState.SCAN_SURFACE, c.update(400, t, good))
        assertEquals(CoachingState.SCAN_SURFACE, c.update(600, t, good))
        assertEquals(CoachingState.READY, c.update(700, t, good))
    }

    @Test fun staysReadyBrieflyWithoutHits() {
        val c = CoachingTracker()
        c.update(0, t, good); c.update(300, t, good)
        assertEquals(CoachingState.READY, c.update(700, t, none))
        assertEquals(CoachingState.READY, c.update(800, t, none))
        assertEquals(CoachingState.SCAN_SURFACE, c.update(900, t, none))
    }

    @Test fun trackingLossDropsReadyImmediately() {
        val c = CoachingTracker()
        c.update(0, t, good); c.update(300, t, good)
        assertEquals(CoachingState.MOVE_SLOWLY, c.update(350, TrackingStatus.PAUSED, good))
    }
}
