package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Vec3
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReticleSmootherTest {
    private val cam = Vec3(0f, 0f, 0f)
    private fun onAxis(d: Float) = Vec3(0f, 0f, -d)

    @Test fun firstSampleIsPassedThrough() {
        val s = ReticleSmoother()
        val out = s.update(cam, onAxis(1f))!!
        assertEquals(-1f, out.z, 1e-6f)
    }

    @Test fun noisyDepthOnStillCameraConvergesToMedian() {
        val s = ReticleSmoother(window = 9)
        val rnd = Random(7)
        var out = Vec3(0f, 0f, 0f)
        // +-1 cm depth noise at 1 m (Pixel depth is ~1% at 1 m).
        repeat(30) { out = s.update(cam, onAxis(1f + (rnd.nextFloat() - 0.5f) * 0.02f))!! }
        assertEquals(-1f, out.z, 0.004f)
        assertEquals(0f, out.x, 1e-6f)
    }

    @Test fun singleOutlierIsRejected() {
        val s = ReticleSmoother(window = 7)
        repeat(6) { s.update(cam, onAxis(1f)) }
        val out = s.update(cam, onAxis(1.025f))!! // 2.5 cm spike: under the jump threshold, median wins
        assertEquals(-1f, out.z, 1e-6f)
    }

    @Test fun outputStaysOnCurrentRay() {
        val s = ReticleSmoother()
        repeat(5) { s.update(cam, onAxis(1f)) }
        // Camera turned slightly: hit 2 mm to the side, still inside the gate; output must be on the new ray.
        val raw = Vec3(0.002f, 0f, -1.01f)
        val out = s.update(cam, raw)!!
        val dir = raw.normalized()
        val perp = (out - dir * (out dot dir)).length()
        assertEquals(0f, perp, 1e-5f)
        assertEquals(1f, out.length(), 0.006f)
    }

    @Test fun panningDropsOldSamples() {
        val s = ReticleSmoother()
        repeat(5) { s.update(cam, onAxis(1f)) }
        // Moved 5 cm sideways on a different-depth surface: old samples are outside the ray gate.
        val out = s.update(cam, Vec3(0.05f, 0f, -1.02f))!!
        assertEquals(Vec3(0.05f, 0f, -1.02f).length(), out.length(), 1e-5f)
    }

    @Test fun depthJumpResetsWindow() {
        val s = ReticleSmoother()
        repeat(7) { s.update(cam, onAxis(1f)) }
        assertEquals(-1.5f, s.update(cam, onAxis(1.5f))!!.z, 1e-6f)
        // Window restarted at the new depth: back to 1.5 even with another 1.5 sample.
        assertEquals(-1.5f, s.update(cam, onAxis(1.5f))!!.z, 1e-6f)
    }

    @Test fun keyChangeResets() {
        val s = ReticleSmoother()
        repeat(7) { s.update(cam, onAxis(1f), key = "DEPTH") }
        val out = s.update(cam, onAxis(1.02f), key = "PLANE")!!
        assertEquals(-1.02f, out.z, 1e-6f)
    }

    @Test fun noHitResets() {
        val s = ReticleSmoother()
        repeat(7) { s.update(cam, onAxis(1f)) }
        assertNull(s.update(cam, null))
        assertEquals(-1.02f, s.update(cam, onAxis(1.02f))!!.z, 1e-6f)
    }

    @Test fun cameraMovingAlongRayIsHandled() {
        val s = ReticleSmoother()
        repeat(5) { s.update(cam, onAxis(1f)) }
        // Stepped 1 cm towards the surface: old samples are re-measured from the new camera position.
        val c2 = Vec3(0f, 0f, -0.01f)
        val out = s.update(c2, Vec3(0f, 0f, -1f))!!
        assertTrue(kotlin.math.abs(out.z + 1f) < 1e-5f)
    }
}
