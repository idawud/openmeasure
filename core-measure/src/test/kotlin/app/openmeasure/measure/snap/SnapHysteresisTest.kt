package app.openmeasure.measure.snap

import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.projection.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapHysteresisTest {
    /** Toy projection: world x,y in metres -> 1000 px per metre. */
    private val project: (Vec3) -> ScreenPoint? = { ScreenPoint(it.x * 1000f, it.y * 1000f) }
    private fun at(px: Float, py: Float = 0f) = ScreenPoint(px, py)
    private fun edge(px: Float, py: Float = 0f) = SnapTarget(Vec3(px / 1000f, py / 1000f, 0f), SnapKind.EDGE, 0.8f)
    private fun corner(px: Float, py: Float = 0f) = SnapTarget(Vec3(px / 1000f, py / 1000f, 0f), SnapKind.CORNER, 0.8f)

    private val hyst = SnapHysteresis(acquireRadiusPx = 20f, releaseRadiusPx = 40f)

    @Test fun acquiresWithinRadiusAndTicksOnce() {
        val a = hyst.update(at(0f), edge(15f), project)
        assertEquals(edge(15f), a.target)
        assertTrue(a.acquired)
        val b = hyst.update(at(1f), edge(15f), project)
        assertFalse(b.acquired)
    }

    @Test fun doesNotAcquireBeyondRadius() {
        assertNull(hyst.update(at(0f), edge(25f), project).target)
    }

    @Test fun holdsThroughDropoutsUntilReleaseRadius() {
        hyst.update(at(0f), edge(10f), project)
        // Detection lost but reticle still within 40 px of the target: keep it, no new tick.
        val held = hyst.update(at(-25f), null, project)
        assertEquals(edge(10f), held.target)
        assertFalse(held.acquired)
        // Beyond the release radius: let go.
        assertNull(hyst.update(at(-35f), null, project).target)
    }

    @Test fun doesNotFlickerBetweenNoneAndEdge() {
        var ticks = 0
        for (i in 0 until 30) {
            val fresh = if (i % 2 == 0) edge(18f) else null
            val u = hyst.update(at(if (i % 3 == 0) 2f else -2f), fresh, project)
            assertEquals(edge(18f), u.target)
            if (u.acquired) ticks++
        }
        assertEquals(1, ticks)
    }

    @Test fun followsAlongEdgeButIgnoresOtherEdges() {
        hyst.update(at(0f, 0f), edge(10f, 0f), project)
        // Sliding along the edge: new detection 8 px away from held -> follow.
        assertEquals(edge(10f, 8f), hyst.update(at(0f, 8f), edge(10f, 8f), project).target)
        // A different edge 30 px from the held one (but near the reticle): keep the held one.
        assertEquals(edge(10f, 8f), hyst.update(at(-10f, 8f), edge(-20f, 8f), project).target)
    }

    @Test fun cornerUpgradesEdgeAndHoldsAgainstEdges() {
        hyst.update(at(0f), edge(10f), project)
        val up = hyst.update(at(0f), corner(12f, 5f), project)
        assertEquals(SnapKind.CORNER, up.target!!.kind)
        assertTrue(up.acquired)
        // Edge detections near the held corner don't downgrade it.
        val held = hyst.update(at(2f), edge(10f), project)
        assertEquals(corner(12f, 5f), held.target)
        assertFalse(held.acquired)
    }

    @Test fun reacquireAfterReleaseTicksAgain() {
        hyst.update(at(0f), edge(10f), project)
        hyst.update(at(100f), null, project)
        assertNull(hyst.current)
        assertTrue(hyst.update(at(100f), edge(110f), project).acquired)
    }

    @Test fun behindCameraTargetIsReleased() {
        hyst.update(at(0f), edge(10f), project)
        assertNull(hyst.update(at(0f), null) { null }.target)
    }

    @Test fun resetClears() {
        hyst.update(at(0f), edge(10f), project)
        hyst.reset()
        assertNull(hyst.current)
    }
}
