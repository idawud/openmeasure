package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HitResolverTest {
    private val resolver = HitResolver()
    private val cam = Vec3(0f, 0f, 0f)
    // Floor 1 m below, plane hit straight ahead-down at (0,-1,-1); identity rotation => normal +Y.
    private val floorHit = PlaneHit(Pose(Vec3(0f, -1f, -1f)))

    private fun depth(p: Vec3, conf: Float) = DepthHit(p, depthM = 1f, confidence = conf, validFraction = 1f)

    @Test fun notTrackingAlwaysNone() {
        val r = resolver.resolve(false, cam, depth(Vec3(0f, -1f, -1f), 1f), floorHit)
        assertSame(HitResult.NONE, r)
        assertNull(r.position)
    }

    @Test fun nothingFoundIsNone() {
        assertEquals(HitSource.NONE, resolver.resolve(true, cam, null, null).source)
    }

    @Test fun planeOnlyFallback() {
        val r = resolver.resolve(true, cam, null, floorHit)
        assertEquals(HitSource.PLANE, r.source)
        assertEquals(0.85f, r.confidence, 1e-5f)
        assertEquals(Vec3(0f, 1f, 0f), r.normal)
    }

    @Test fun depthOnlyWhenNoPlane() {
        val r = resolver.resolve(true, cam, depth(Vec3(0f, 0f, -1f), 0.6f), null)
        assertEquals(HitSource.DEPTH, r.source)
        assertEquals(1f, r.distanceM, 1e-5f)
    }

    @Test fun unusableDepthOnlyIsNone() {
        assertEquals(HitSource.NONE, resolver.resolve(true, cam, depth(Vec3(0f, 0f, -1f), 0.05f), null).source)
    }

    @Test fun agreeingDepthSnapsToPlaneAlongRay() {
        // Depth 1 cm below the floor along the same ray: snap back onto the plane.
        val r = resolver.resolve(true, cam, depth(Vec3(0f, -1.01f, -1.01f), 0.5f), floorHit)
        assertEquals(HitSource.PLANE, r.source)
        assertEquals(-1f, r.position!!.y, 1e-5f)
        assertEquals(-1f, r.position!!.z, 1e-5f)
        assertEquals(0.85f, r.confidence, 1e-5f) // max(depth 0.5, plane 0.85)
    }

    @Test fun confidentDepthAboveFloorBeatsPlane() {
        // An object 30 cm above the floor along the view ray.
        val r = resolver.resolve(true, cam, depth(Vec3(0f, -0.7f, -0.7f), 0.8f), floorHit)
        assertEquals(HitSource.DEPTH, r.source)
        assertEquals(-0.7f, r.position!!.y, 1e-5f)
    }

    @Test fun weakDisagreeingDepthFallsBackToPlane() {
        val r = resolver.resolve(true, cam, depth(Vec3(0f, -0.7f, -0.7f), 0.2f), floorHit)
        assertEquals(HitSource.PLANE, r.source)
        assertEquals(-1f, r.position!!.y, 1e-5f)
    }

    @Test fun planeConfidenceFallsWithDistance() {
        assertEquals(0.85f, resolver.planeConfidence(2f), 1e-5f)
        assertEquals(0.425f, resolver.planeConfidence(4.25f), 1e-5f)
        assertEquals(0f, resolver.planeConfidence(10f), 0f)
    }
}
