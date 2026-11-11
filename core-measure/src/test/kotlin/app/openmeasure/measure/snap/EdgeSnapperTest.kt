package app.openmeasure.measure.snap

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

class EdgeSnapperTest {
    private val w = 160
    private val h = 120
    private val k = CameraIntrinsics(fx = 120f, fy = 120f, cx = 79.5f, cy = 59.5f, width = w, height = h)
    private val snapper = EdgeSnapper()

    private fun map(conf: Int? = null, noiseRel: Double = 0.0, seed: Long = 7, f: (Int, Int) -> Int): DepthMap {
        val rnd = Random(seed)
        val d = ShortArray(w * h) { i ->
            val base = f(i % w, i / w)
            val noisy = if (base == 0) 0 else (base * (1 + noiseRel * rnd.nextGaussian())).roundToInt()
            noisy.toShort()
        }
        return DepthMap(w, h, d, conf?.let { c -> ByteArray(w * h) { c.toByte() } })
    }

    /** Near surface at 1.0 m for x < 80, background at 1.5 m. */
    private fun step(conf: Int? = null, noise: Double = 0.0) = map(conf, noise) { x, _ -> if (x < 80) 1000 else 1500 }

    /** Box top (0.8 m) occupies x < 80 && y < 60; floor at 1.2 m elsewhere. */
    private fun box(noise: Double = 0.0) = map(noiseRel = noise) { x, y -> if (x < 80 && y < 60) 800 else 1200 }

    @Test fun stepEdgeSnapsToNearSideRim() {
        val r = snapper.snap(step(), k, 83f, 40f, 6, Pose.IDENTITY)
        assertTrue(r.snapped)
        assertEquals(SnapKind.EDGE, r.kind)
        assertEquals(79, r.pixelX)
        assertEquals(40, r.pixelY)
        assertEquals(4f, r.pixelShift, 1e-4f)
        assertEquals(1.0f, r.depthM, 1e-4f)
        val expected = DepthHitTester.unprojectToCamera(k, 79f, 40f, 1.0f)
        assertTrue(r.position!!.distanceTo(expected) < 1e-4f)
    }

    @Test fun snapAlsoWorksFromNearSideAndAppliesPose() {
        val pose = Pose(Vec3(1f, 2f, 3f))
        val r = snapper.snap(step(), k, 76f, 40f, 6, pose)
        assertEquals(79, r.pixelX)
        val expected = DepthHitTester.unprojectToCamera(k, 79f, 40f, 1.0f) + Vec3(1f, 2f, 3f)
        assertTrue(r.position!!.distanceTo(expected) < 1e-4f)
    }

    @Test fun edgeBeyondMaxRadiusIsNotSnapped() {
        val r = snapper.snap(step(), k, 90f, 40f, 6, Pose.IDENTITY)
        assertFalse(r.snapped)
        assertEquals(SnapKind.NONE, r.kind)
    }

    @Test fun shiftNeverExceedsRadius() {
        for (u in 70..95) {
            val r = snapper.snap(step(), k, u.toFloat(), 40.3f, 5, Pose.IDENTITY)
            if (r.snapped) assertTrue("u=$u shift=${r.pixelShift}", r.pixelShift <= 5f)
        }
    }

    @Test fun boxCornerSnapsToVertex() {
        val r = snapper.snap(box(), k, 82f, 62f, 6, Pose.IDENTITY)
        assertEquals(SnapKind.CORNER, r.kind)
        assertTrue("x=${r.pixelX}", abs(r.pixelX!! - 79) <= 1)
        assertTrue("y=${r.pixelY}", abs(r.pixelY!! - 59) <= 1)
        assertEquals(0.8f, r.depthM, 1e-3f)
    }

    @Test fun boxEdgeFarFromVertexIsEdgeNotCorner() {
        val r = snapper.snap(box(), k, 82f, 30f, 6, Pose.IDENTITY)
        assertEquals(SnapKind.EDGE, r.kind)
        assertEquals(79, r.pixelX)
    }

    @Test fun cornerWinsOverCloserEdge() {
        // Reticle sits right on the vertical rim, 4 px above the vertex: corner still preferred.
        val r = snapper.snap(box(), k, 79f, 55f, 6, Pose.IDENTITY)
        assertEquals(SnapKind.CORNER, r.kind)
    }

    @Test fun noisyFlatWallDoesNotSnap() {
        for (seed in 1L..20L) {
            val wall = map(conf = 255, noiseRel = 0.01, seed = seed) { _, _ -> 1000 }
            for ((u, v) in listOf(80f to 60f, 30f to 30f, 130f to 90f)) {
                assertFalse("seed=$seed", snapper.snap(wall, k, u, v, 6, Pose.IDENTITY).snapped)
            }
        }
    }

    @Test fun steeplySlantedPlaneDoesNotSnap() {
        // Plane: inverse depth affine in u, from 0.6 m at the left to 3 m at the right.
        val plane = map { x, _ -> (1000.0 / (1 / 0.6 + (1 / 3.0 - 1 / 0.6) * x / (w - 1))).roundToInt() }
        assertFalse(snapper.snap(plane, k, 80f, 60f, 8, Pose.IDENTITY).snapped)
    }

    @Test fun noisyStepEdgeStillSnaps() {
        val r = snapper.snap(step(conf = 255, noise = 0.005), k, 82f, 40f, 6, Pose.IDENTITY)
        assertTrue(r.snapped)
        assertTrue("x=${r.pixelX}", abs(r.pixelX!! - 79) <= 1)
        assertNotNull(r.position)
    }

    @Test fun lowSensorConfidenceNeverSnaps() {
        assertFalse(snapper.snap(step(conf = 20), k, 82f, 40f, 6, Pose.IDENTITY).snapped)
        assertTrue(snapper.snap(step(conf = 255), k, 82f, 40f, 6, Pose.IDENTITY).snapped)
    }

    @Test fun invalidBackgroundIsNotAnEdge() {
        // Depth holes (0) behind the object: no measured jump, so no snap.
        val holes = map { x, _ -> if (x < 80) 1000 else 0 }
        assertFalse(snapper.snap(holes, k, 82f, 40f, 6, Pose.IDENTITY).snapped)
    }

    @Test fun isolatedOutlierIsIgnored() {
        val spike = map { x, y -> if (x == 80 && y == 40) 700 else 1000 }
        assertFalse(snapper.snap(spike, k, 82f, 40f, 6, Pose.IDENTITY).snapped)
    }

    @Test fun outOfBoundsOrZeroRadiusReturnsNone() {
        assertFalse(snapper.snap(step(), k, -5f, 40f, 6, Pose.IDENTITY).snapped)
        assertFalse(snapper.snap(step(), k, 82f, 40f, 0, Pose.IDENTITY).snapped)
    }

    @Test fun farAwayEdgeHasNoConfidence() {
        val far = map { x, _ -> if (x < 80) 6500 else 7900 }
        assertFalse(snapper.snap(far, k, 82f, 40f, 6, Pose.IDENTITY).snapped)
    }
}
