package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Quaternion
import app.openmeasure.measure.geometry.Ray
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class RectDetectorTest {
    private val w = 160
    private val h = 120
    private val k = CameraIntrinsics(fx = 120f, fy = 120f, cx = 79.5f, cy = 59.5f, width = w, height = h)
    private val detector = RectDetector()

    /** Patch = region of [plane] inside [inside] (2D test in [basis] coords); everything else hits [background]. */
    private fun render(
        plane: Plane, basis: PlaneBasis, inside: (Vec2) -> Boolean, background: Plane, noiseMm: Double = 0.0,
    ): DepthMap {
        val rnd = Random(7)
        val depth = ShortArray(w * h)
        for (v in 0 until h) for (u in 0 until w) {
            val ray = Ray(Vec3.ZERO, Vec3((u - k.cx) / k.fx, -(v - k.cy) / k.fy, -1f))
            val onPatch = plane.intersect(ray)?.takeIf { inside(basis.to2d(it)) }
            val p = onPatch ?: background.intersect(ray)
            val z = if (p == null) 0.0 else -p.z * 1000.0 + rnd.nextGaussian() * noiseMm
            depth[v * w + u] = z.roundToInt().coerceIn(0, 65535).toShort()
        }
        return DepthMap(w, h, depth)
    }

    private fun rectTest(width: Double, height: Double, rotDeg: Double): (Vec2) -> Boolean {
        val a = Math.toRadians(rotDeg)
        return { q ->
            val x = q.x * cos(a) + q.y * sin(a)
            val y = -q.x * sin(a) + q.y * cos(a)
            kotlin.math.abs(x) <= width / 2 && kotlin.math.abs(y) <= height / 2
        }
    }

    private val floor = Plane(Vec3(0f, 0f, -1.3f), Vec3(0f, 0f, 1f))

    private fun frontoPatch(z: Float = 1.0f): Pair<Plane, PlaneBasis> {
        val plane = Plane(Vec3(0f, 0f, -z), Vec3(0f, 0f, 1f))
        return plane to PlaneBasis.of(plane.point, plane.normal, Vec3(1f, 0f, 0f))
    }

    private fun sideLengths(c: List<Vec3>) = (0..3).map { c[it].distanceTo(c[(it + 1) % 4]).toDouble() }

    @Test
    fun frontoParallelBoxTop() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, rectTest(0.30, 0.20, 0.0), floor)
        val r = detector.detect(depth, k, 79.5f, 59.5f, Pose.IDENTITY)
        assertNotNull(r)
        r!!
        assertEquals(0.30f, r.widthM, 0.01f)
        assertEquals(0.20f, r.heightM, 0.01f)
        assertTrue(r.fillRatio >= 0.85f)
        assertEquals(1.0f, r.meanDepthM, 0.01f)
        assertEquals(1f, r.plane.normal.z, 1e-3f) // towards the camera
        val q = QuadAnalyzer().analyze(r.corners)
        assertTrue(q.isRectangle)
        assertEquals(0.06, q.areaM2, 0.005)
    }

    @Test
    fun tiltedAndRotatedRectangle() {
        val tilt = Math.toRadians(25.0)
        val normal = Vec3(0f, sin(tilt).toFloat(), cos(tilt).toFloat())
        val plane = Plane(Vec3(0.02f, -0.01f, -1.1f), normal)
        val basis = PlaneBasis.of(plane.point, normal, Vec3(1f, 0f, 0f))
        val depth = render(plane, basis, rectTest(0.36, 0.24, 20.0), Plane(Vec3(0f, 0f, -1.6f), Vec3(0f, 0f, 1f)))
        val r = detector.detect(depth, k, 81f, 60f, Pose.IDENTITY)
        assertNotNull(r)
        r!!
        assertEquals(0.36f, r.widthM, 0.005f)
        assertEquals(0.24f, r.heightM, 0.005f)
        val sides = sideLengths(r.corners).sorted()
        assertEquals(0.24, sides[0], 0.01)
        assertEquals(0.36, sides[3], 0.01)
        assertEquals(1f, r.plane.normal dot normal, 1e-3f)
        assertTrue(QuadAnalyzer().analyze(r.corners).isRectangle)
    }

    @Test
    fun noisyDepthStillDetected() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, rectTest(0.30, 0.20, 10.0), floor, noiseMm = 3.0)
        val r = detector.detect(depth, k, 79.5f, 59.5f, Pose.IDENTITY)
        assertNotNull(r)
        r!!
        assertEquals(0.30f, r.widthM, 0.005f)
        assertEquals(0.20f, r.heightM, 0.005f)
    }

    @Test
    fun cornersAreInWorldSpace() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, rectTest(0.30, 0.20, 0.0), floor)
        val s = sqrt(0.5).toFloat()
        val pose = Pose(Vec3(1f, 2f, 3f), Quaternion(0f, s, 0f, s))
        val local = detector.detect(depth, k, 79.5f, 59.5f, Pose.IDENTITY)!!
        val world = detector.detect(depth, k, 79.5f, 59.5f, pose)!!
        for (i in 0..3) {
            val expected = pose.transformPoint(local.corners[i])
            assertEquals(0f, expected.distanceTo(world.corners[i]), 1e-4f)
        }
    }

    @Test
    fun borderTouchingRegionIsRejected() {
        val (plane, basis) = frontoPatch()
        // 1.6 m wide at 1 m spans more than the 1.33 m field of view.
        val depth = render(plane, basis, rectTest(1.6, 0.2, 0.0), floor)
        assertNull(detector.detect(depth, k, 79.5f, 59.5f, Pose.IDENTITY))
    }

    @Test
    fun seedOnBackgroundIsRejected() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, rectTest(0.30, 0.20, 0.0), floor)
        assertNull(detector.detect(depth, k, 10f, 10f, Pose.IDENTITY))
    }

    @Test
    fun discIsNotARectangle() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, { it.length() <= 0.15 }, floor)
        assertNull(detector.detect(depth, k, 79.5f, 59.5f, Pose.IDENTITY))
    }

    @Test
    fun seedOutsideImageOrOnHoleIsRejected() {
        val (plane, basis) = frontoPatch()
        val depth = render(plane, basis, rectTest(0.30, 0.20, 0.0), floor)
        assertNull(detector.detect(depth, k, -5f, 10f, Pose.IDENTITY))
        val empty = DepthMap(w, h, ShortArray(w * h))
        assertNull(detector.detect(empty, k, 79.5f, 59.5f, Pose.IDENTITY))
    }

    @Test
    fun minAreaRectOfRotatedPoints() {
        val a = Math.toRadians(30.0)
        val d = Vec2(cos(a), sin(a))
        val p = Vec2(-sin(a), cos(a))
        val pts = listOf(Vec2(0.0, 0.0), d * 2.0, d * 2.0 + p, p, d + p * 0.5)
        val r = MinAreaRect.of(pts)!!
        assertEquals(2.0, r.width, 1e-9)
        assertEquals(1.0, r.height, 1e-9)
        assertEquals(4, MinAreaRect.convexHull(pts).size)
    }
}
