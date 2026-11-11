package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnchorPoseFilterTest {
    private val p0 = Vec3(0f, 0f, -1f)
    private fun f() = AnchorPoseFilter<Int>().also { it.seed(1, p0) }

    @Test fun unknownIdIgnored() {
        assertNull(AnchorPoseFilter<Int>().update(9, p0, true))
    }

    @Test fun subMillimetreJitterIsFrozen() {
        val f = f()
        repeat(20) { i -> assertNull(f.update(1, p0 + Vec3(if (i % 2 == 0) 0.001f else -0.001f, 0f, 0f), true)) }
        assertEquals(p0, f.accepted(1))
    }

    @Test fun refinementIsFollowed() {
        val f = f()
        val moved = p0 + Vec3(0.01f, 0f, 0f)
        assertEquals(moved, f.update(1, moved, true))
        assertEquals(moved, f.accepted(1))
    }

    @Test fun notTrackingHolds() {
        val f = f()
        assertNull(f.update(1, p0 + Vec3(0.02f, 0f, 0f), false))
        assertNull(f.update(1, null, true))
        assertEquals(p0, f.accepted(1))
    }

    @Test fun oneFrameJumpIsRejected() {
        val f = f()
        assertNull(f.update(1, p0 + Vec3(0.2f, 0f, 0f), true))
        assertNull(f.update(1, p0, true))
        assertEquals(p0, f.accepted(1))
    }

    @Test fun consistentJumpIsAcceptedAfterConfirmation() {
        val f = AnchorPoseFilter<Int>(confirmFrames = 5).also { it.seed(1, p0) }
        val far = p0 + Vec3(0f, 0.1f, 0f)
        repeat(4) { assertNull(f.update(1, far, true)) }
        assertEquals(far, f.update(1, far, true))
    }

    @Test fun trackingLossResetsConfirmation() {
        val f = AnchorPoseFilter<Int>(confirmFrames = 3).also { it.seed(1, p0) }
        val far = p0 + Vec3(0f, 0.1f, 0f)
        f.update(1, far, true); f.update(1, far, true)
        f.update(1, far, false)
        assertNull(f.update(1, far, true))
        assertNull(f.update(1, far, true))
        assertEquals(far, f.update(1, far, true))
    }

    @Test fun seedAndRemove() {
        val f = f()
        val p1 = Vec3(1f, 2f, 3f)
        f.seed(1, p1)
        assertEquals(p1, f.accepted(1))
        f.remove(1)
        assertNull(f.accepted(1))
    }
}
