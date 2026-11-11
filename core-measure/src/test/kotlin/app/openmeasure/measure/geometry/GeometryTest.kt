package app.openmeasure.measure.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class GeometryTest {
    private fun assertVec(e: Vec3, a: Vec3?, eps: Float = 1e-4f) {
        assertNotNull(a)
        assertEquals(e.x, a!!.x, eps); assertEquals(e.y, a.y, eps); assertEquals(e.z, a.z, eps)
    }

    @Test fun quaternionRotates90DegAboutY() {
        val h = sqrt(0.5f)
        val q = Quaternion(0f, h, 0f, h) // +90 deg about +Y: +X -> -Z
        assertVec(Vec3(0f, 0f, -1f), q.rotate(Vec3(1f, 0f, 0f)))
    }

    @Test fun poseRotatesThenTranslates() {
        val h = sqrt(0.5f)
        val pose = Pose(Vec3(1f, 2f, 3f), Quaternion(0f, 0f, h, h)) // +90 about Z: +X -> +Y
        assertVec(Vec3(1f, 3f, 3f), pose.transformPoint(Vec3(1f, 0f, 0f)))
    }

    @Test fun rayPlaneIntersection() {
        val floor = Plane(Vec3(0f, -1.5f, 0f), Vec3(0f, 1f, 0f))
        val hit = floor.intersect(Ray(Vec3.ZERO, Vec3(0f, -1f, -1f)))
        assertVec(Vec3(0f, -1.5f, -1.5f), hit)
    }

    @Test fun rayParallelOrBehindPlaneMisses() {
        val floor = Plane(Vec3(0f, -1.5f, 0f), Vec3(0f, 1f, 0f))
        assertNull(floor.intersect(Ray(Vec3.ZERO, Vec3(0f, 0f, -1f))))
        assertNull(floor.intersect(Ray(Vec3.ZERO, Vec3(0f, 1f, 0f))))
    }
}
