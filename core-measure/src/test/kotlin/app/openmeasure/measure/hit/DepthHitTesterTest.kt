package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Quaternion
import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DepthHitTesterTest {
    private val k = CameraIntrinsics(fx = 120f, fy = 120f, cx = 79.5f, cy = 59.5f, width = 160, height = 120)
    private val tester = DepthHitTester()

    private fun flat(mm: Int, conf: Int? = null, w: Int = 160, h: Int = 120) = DepthMap(
        w, h, ShortArray(w * h) { mm.toShort() }, conf?.let { c -> ByteArray(w * h) { c.toByte() } },
    )

    @Test fun unprojectPrincipalPointLooksDownMinusZ() {
        val p = DepthHitTester.unprojectToCamera(k, 79.5f, 59.5f, 1.0f)
        assertEquals(0f, p.x, 1e-6f); assertEquals(0f, p.y, 1e-6f); assertEquals(-1f, p.z, 1e-6f)
    }

    @Test fun unprojectOffCentreFlipsImageY() {
        // 24 px right and 12 px down of centre at 2 m: x = 24*2/120 = 0.4, y_up = -0.2
        val p = DepthHitTester.unprojectToCamera(k, 103.5f, 71.5f, 2.0f)
        assertEquals(0.4f, p.x, 1e-5f); assertEquals(-0.2f, p.y, 1e-5f); assertEquals(-2f, p.z, 1e-6f)
    }

    @Test fun intrinsicsScaleWithPixelCentreConvention() {
        val big = CameraIntrinsics(480f, 480f, 319.5f, 239.5f, 640, 480)
        val s = big.scaledTo(160, 120)
        assertEquals(120f, s.fx, 1e-5f); assertEquals(79.5f, s.cx, 1e-5f); assertEquals(59.5f, s.cy, 1e-5f)
        val (u, v) = s.normalizedToPixel(0.5f, 0.5f)
        assertEquals(79.5f, u, 1e-5f); assertEquals(59.5f, v, 1e-5f)
    }

    @Test fun hitTestAppliesCameraPose() {
        val h = sqrt(0.5f)
        // Camera at (1,1.5,0) yawed +90 deg about Y: camera -Z maps to world -X.
        val pose = Pose(Vec3(1f, 1.5f, 0f), Quaternion(0f, h, 0f, h))
        val hit = tester.hitTest(flat(1000, 255), k, 79.5f, 59.5f, pose)
        assertNotNull(hit)
        assertEquals(0f, hit!!.position.x, 1e-4f)
        assertEquals(1.5f, hit.position.y, 1e-4f)
        assertEquals(0f, hit.position.z, 1e-4f)
        assertEquals(1f, hit.depthM, 1e-6f)
    }

    @Test fun patchMedianRejectsOutliersAndHoles() {
        val map = flat(1000, w = 20, h = 20)
        map.depthMm[10 * 20 + 10] = 3000      // spike at centre
        map.depthMm[9 * 20 + 9] = 0           // hole
        map.depthMm[11 * 20 + 11] = 200       // near outlier
        val s = tester.samplePatch(map, 10f, 10f)!!
        assertEquals(1000f, s.medianMm, 0f)
        assertEquals(0f, s.madMm, 0f)
        assertEquals(24f / 25f, s.validFraction, 1e-6f)
        assertEquals(22f / 25f, s.inlierFraction, 1e-6f)
    }

    @Test fun patchMedianEvenCountAverages() {
        assertEquals(1500f, DepthHitTester.median(intArrayOf(1000, 1400, 1600, 2000)), 0f)
    }

    @Test fun mostlyInvalidPatchGivesNoHit() {
        val map = flat(0, w = 20, h = 20)
        map.depthMm[10 * 20 + 10] = 1000
        assertNull(tester.hitTest(map, k, 10f, 10f, Pose.IDENTITY))
        assertNull(tester.hitTest(flat(1000), k, -5f, 10f, Pose.IDENTITY)) // out of bounds
    }

    @Test fun confidenceHighOnCleanCloseSurface() {
        val hit = tester.hitTest(flat(1000, 255), k, 80f, 60f, Pose.IDENTITY)!!
        assertEquals(1f, hit.confidence, 1e-5f)
    }

    @Test fun confidenceDropsAtDepthEdge() {
        val map = flat(1000, 255, w = 20, h = 20)
        for (y in 0 until 20) for (x in 10 until 20) map.depthMm[y * 20 + x] = 1600.toShort()
        val edge = tester.hitTest(map, k, 10f, 10f, Pose.IDENTITY)!!
        assertEquals(1.6f, edge.depthM, 1e-6f) // median picks the majority surface
        assertEquals(15f / 25f, edge.confidence, 1e-5f)
        assertTrue(edge.confidence < tester.hitTest(map, k, 15f, 10f, Pose.IDENTITY)!!.confidence)
    }

    @Test fun confidenceFallsWithDistanceAndSensorConfidence() {
        val near = tester.confidence(1f, 0f, 1f, 1f)
        val far = tester.confidence(4.25f, 0f, 1f, 1f)
        val tooFar = tester.confidence(7f, 0f, 1f, 1f)
        assertEquals(1f, near, 1e-6f); assertEquals(0.5f, far, 1e-5f); assertEquals(0f, tooFar, 0f)
        assertEquals(0.5f, tester.confidence(1f, 0f, 1f, 0.25f), 1e-5f) // sqrt(0.25)
        assertEquals(0.7f, tester.confidence(1f, 0f, 1f, null), 1e-5f)  // no confidence image
    }

    @Test fun confidenceDropsWithSpread() {
        // MAD 20 mm at 1 m -> relSpread ~0.0297 -> factor ~0.506
        assertEquals(1f - 1.4826f * 0.02f / 0.06f, tester.confidence(1f, 0.02f, 1f, 1f), 1e-4f)
    }
}
