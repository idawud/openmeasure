package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SurfaceFuserTest {
    private val target = Vec3(0f, 0f, -0.5f)
    private val slantN = Vec3(0.3f, 0.2f, 1f).normalized()

    /** 5x5 grid (+-4 cm, 2 cm steps) on the plane through [centre]; optional gaussian noise along the normal. */
    private fun planePoints(centre: Vec3, normal: Vec3, rnd: Random? = null, sigmaM: Float = 0f): List<Vec3> {
        val n = normal.normalized()
        val u = (n cross Vec3(0f, 1f, 0f)).normalized()
        val v = n cross u
        val out = ArrayList<Vec3>(25)
        for (i in -2..2) for (j in -2..2) {
            var p = centre + u * (i * 0.02f) + v * (j * 0.02f)
            if (rnd != null) p += n * (sigmaM * rnd.nextGaussian().toFloat())
            out += p
        }
        return out
    }

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) * 0.5f
    }

    @Test fun fusedHitBeatsSingleFrameMedianOnNoisyMovingCamera() {
        val rnd = Random(42)
        val fuser = SurfaceFuser()
        val frames = 20
        var rawErr = 0f
        var fusedErr = 0f
        var lastFusedErr = Float.MAX_VALUE
        for (i in 0 until frames) {
            val cam = Vec3(-0.1f + 0.2f * i / (frames - 1), 0f, 0f)
            val dir = (target - cam).normalized()
            // Single-frame "median depth": 25 noisy samples (8 mm along the normal) -> median along the ray.
            val pts = planePoints(target, slantN, rnd, 0.008f)
            val raw = cam + dir * median(pts.map { (it - cam) dot dir })
            val fused = fuser.update(cam, dir, pts, "DEPTH", raw)
            assertNotNull("frame $i", fused)
            rawErr += raw.distanceTo(target)
            fusedErr += fused!!.distanceTo(target)
            lastFusedErr = fused.distanceTo(target)
        }
        assertTrue("fused ${lastFusedErr * 1000} mm", lastFusedErr < 0.003f)
        assertTrue("mean fused ${fusedErr / frames} vs raw ${rawErr / frames}", fusedErr < rawErr)
    }

    @Test fun tooFewOrNonPlanarPointsGiveNull() {
        val fuser = SurfaceFuser()
        val cam = Vec3.ZERO
        val dir = (target - cam).normalized()
        val flat = planePoints(target, slantN)
        assertNull(fuser.update(cam, dir, flat.take(11), "DEPTH", target))
        assertNotNull(fuser.update(cam, dir, flat.take(12), "DEPTH", target)) // positive control

        val rnd = Random(1)
        val cube = List(100) { Vec3(target.x + rnd.nextFloat() * 0.1f, target.y + rnd.nextFloat() * 0.1f, target.z + rnd.nextFloat() * 0.1f) }
        assertNull(SurfaceFuser().update(cam, dir, cube, "DEPTH", target))
        assertNull(SurfaceFuser().update(cam, dir, null, "DEPTH", target))
    }

    @Test fun keyChangeReplacesHeldPlane() {
        val cam = Vec3.ZERO
        val dir = Vec3(0f, 0f, -1f)
        val n = Vec3(0f, 0f, 1f)
        val fuser = SurfaceFuser()
        repeat(5) { fuser.update(cam, dir, planePoints(Vec3(0f, 0f, -0.5f), n), "A", Vec3(0f, 0f, -0.5f)) }
        // Same key: 2 cm step is blended (alpha 0.3 of the way) ...
        val blended = fuser.update(cam, dir, planePoints(Vec3(0f, 0f, -0.52f), n), "A", Vec3(0f, 0f, -0.52f))!!
        assertEquals(-0.506f, blended.z, 1e-3f)
        // ... but a key change replaces the held plane outright.
        val fresh = SurfaceFuser()
        repeat(5) { fresh.update(cam, dir, planePoints(Vec3(0f, 0f, -0.5f), n), "A", Vec3(0f, 0f, -0.5f)) }
        val out = fresh.update(cam, dir, planePoints(Vec3(0f, 0f, -0.52f), n), "B", Vec3(0f, 0f, -0.52f))!!
        assertEquals(-0.52f, out.z, 1e-4f)
    }

    @Test fun grazingRayGivesNull() {
        val cam = Vec3.ZERO
        val dir = Vec3(0f, 0f, -1f)
        val centre = Vec3(0f, 0f, -1f)
        // Normal 85 deg from the ray: grazing.
        val a = Math.toRadians(85.0).toFloat()
        val grazingN = Vec3(sin(a), 0f, cos(a))
        assertNull(SurfaceFuser().update(cam, dir, planePoints(centre, grazingN), "DEPTH", centre))
        // Normal 60 deg from the ray: fine (positive control).
        val b = Math.toRadians(60.0).toFloat()
        val okN = Vec3(sin(b), 0f, cos(b))
        assertNotNull(SurfaceFuser().update(cam, dir, planePoints(centre, okN), "DEPTH", centre))
    }

    @Test fun fusedHitFarFromRawMedianIsRejected() {
        val cam = Vec3.ZERO
        val dir = (target - cam).normalized()
        val pts = planePoints(target, slantN)
        // Raw hit 6 cm beyond the fused one: outside the 5 cm guard.
        assertNull(SurfaceFuser().update(cam, dir, pts, "DEPTH", target + dir * 0.06f))
        assertTrue(abs(SurfaceFuser().update(cam, dir, pts, "DEPTH", target + dir * 0.04f)!!.z - target.z) < 1e-3f)
    }
}
