package app.openmeasure.measure.height

import app.openmeasure.measure.confidence.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HeightSmootherTest {
    private fun ok(h: Float, level: ConfidenceLevel = ConfidenceLevel.HIGH) =
        HeightResult(h, level, HeightReason.OK, 0.9f, 2.5f, FloorSource.PLANE)

    @Test fun collectsBeforeShowing() {
        val s = HeightSmoother()
        repeat(4) { assertTrue(s.add(ok(1.75f)).isCollecting) }
        assertEquals(1.75f, s.add(ok(1.75f)).heightMeters!!, 1e-6f)
    }

    @Test fun lowNoise_isStableAndHigh() {
        val s = HeightSmoother()
        val rnd = Random(1)
        var out = SmoothedHeight.EMPTY
        repeat(15) { out = s.add(ok(1.75f + (rnd.nextFloat() - 0.5f) * 0.006f)) }
        assertTrue(out.stable)
        assertEquals(1.75f, out.heightMeters!!, 0.003f)
        assertEquals(ConfidenceLevel.HIGH, out.confidenceLevel)
    }

    @Test fun highNoise_isUnstableAndCappedMedium() {
        val s = HeightSmoother()
        val rnd = Random(2)
        var out = SmoothedHeight.EMPTY
        repeat(15) { out = s.add(ok(1.75f + (rnd.nextFloat() - 0.5f) * 0.08f)) }
        assertFalse(out.stable)
        assertEquals(ConfidenceLevel.MEDIUM, out.confidenceLevel)
        assertEquals(1.75f, out.heightMeters!!, 0.03f)
    }

    @Test fun outliersRejected() {
        val s = HeightSmoother()
        var out = SmoothedHeight.EMPTY
        repeat(15) { i -> out = s.add(ok(if (i % 5 == 2) 2.3f else 1.75f + (i % 3) * 0.002f)) }
        assertEquals(1.752f, out.heightMeters!!, 0.003f)
        assertEquals(12, out.sampleCount)
        assertTrue(out.stable)
    }

    @Test fun invalidFrameHidesNumber_andLongDropoutResets() {
        val s = HeightSmoother(resetAfterInvalid = 3)
        repeat(10) { s.add(ok(1.75f)) }
        val partial = s.add(HeightResult.fail(HeightReason.PARTIAL_BODY))
        assertNull(partial.heightMeters)
        assertEquals(HeightReason.PARTIAL_BODY, partial.reason)
        assertEquals(1.75f, s.add(ok(1.75f)).heightMeters!!, 1e-6f) // brief dropout keeps buffer
        repeat(3) { s.add(HeightResult.fail(HeightReason.NO_PERSON)) }
        assertTrue(s.add(ok(1.60f)).isCollecting)
    }

    @Test fun badgeIsWeakerOfLatestAndTypical() {
        val s = HeightSmoother()
        repeat(10) { s.add(ok(1.75f)) }
        assertEquals(ConfidenceLevel.LOW, s.add(ok(1.75f, ConfidenceLevel.LOW)).confidenceLevel)
    }
}
