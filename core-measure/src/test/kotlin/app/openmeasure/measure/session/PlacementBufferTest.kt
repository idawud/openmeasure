package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementBufferTest {
    private val still = Vec3(0f, 0f, 0f)

    /** Feeds a still camera at 10 ms steps from t0 to t1 (inclusive), each frame with value = t. */
    private fun feedStill(b: PlacementBuffer<Long>, t0: Long, t1: Long) {
        var t = t0
        while (t <= t1) { b.record(t, still, t); t += 10 }
    }

    @Test fun picksSampleAboutLookbackOldNotNewest() {
        val b = PlacementBuffer<Long>()
        feedStill(b, 0, 500)
        val p = b.pick(500)!!
        // Newest eligible is t <= 500 - 120 = 380.
        assertEquals(380L, p.value)
        assertEquals(380L, p.tMs)
    }

    @Test fun skipsFastSamples() {
        val b = PlacementBuffer<Long>()
        feedStill(b, 0, 300)
        // Fast camera from t=310..420: 1 m per 10 ms = 100 m/s.
        var t = 310L
        var x = 0f
        while (t <= 420) { x += 0.1f; b.record(t, Vec3(x, 0f, 0f), t); t += 10 }
        // Eligible: t <= 500-120=380, but samples 310..380 are fast, so the newest still one is t=300.
        val p = b.pick(500)!!
        assertEquals(300L, p.value)
        assertTrue(p.speedMps <= 0.25f)
    }

    @Test fun nullWhenOnlyFastOrInvalid() {
        val b = PlacementBuffer<Long>()
        // First sample has no speed history, so it is invalid (null) to keep the "only fast" premise.
        b.record(0, still, null)
        var x = 0f
        var t = 10L
        while (t <= 300) { x += 0.1f; b.record(t, Vec3(x, 0f, 0f), t); t += 10 }
        assertNull(b.pick(420))

        val invalid = PlacementBuffer<Long>()
        var s = 0L
        while (s <= 300) { invalid.record(s, still, null); s += 10 }
        assertNull(invalid.pick(420))
    }

    @Test fun nullWhenNoSampleOldEnough() {
        val b = PlacementBuffer<Long>()
        feedStill(b, 0, 50)
        assertNull(b.pick(100))
    }

    @Test fun oldSamplesEvicted() {
        val b = PlacementBuffer<Long>()
        feedStill(b, 0, 1000)
        // Only samples with t >= 1000 - 400 = 600 remain; the one at 380 is gone.
        assertNull(b.pick(500))
        assertEquals(880L, b.pick(1000)!!.value)
    }

    @Test fun steadyAndCurrentSpeed() {
        val b = PlacementBuffer<Long>()
        assertTrue(b.isSteady())
        feedStill(b, 0, 100)
        assertEquals(0f, b.currentSpeed, 1e-6f)
        assertTrue(b.isSteady())
        b.record(110, Vec3(0.01f, 0f, 0f), 110) // 0.01 m / 0.01 s = 1 m/s
        assertFalse(b.isSteady())
    }

    @Test fun clearEmptiesBuffer() {
        val b = PlacementBuffer<Long>()
        feedStill(b, 0, 300)
        b.clear()
        assertNull(b.pick(300))
        assertEquals(0f, b.currentSpeed, 1e-6f)
    }
}
