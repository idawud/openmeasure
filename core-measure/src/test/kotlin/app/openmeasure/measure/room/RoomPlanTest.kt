package app.openmeasure.measure.room

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlanTest {
    /** Floor corners in world space (Y up); plan (x, y) = world (x, −z). */
    private fun floor(vararg xz: Pair<Double, Double>, y: Float = -1.4f) = xz.map { (x, z) -> Vec3(x.toFloat(), y, z.toFloat()) }

    // 4 m x 3 m room, walked counter-clockwise seen from above (plan y = −z).
    private val ccwRoom = floor(0.0 to 0.0, 4.0 to 0.0, 4.0 to -3.0, 0.0 to -3.0)

    @Test fun rectangleBasics() {
        val p = RoomPlan.fromCorners(ccwRoom)
        assertTrue(p.isValid)
        assertEquals(12.0, p.areaM2, 1e-4)
        assertEquals(14.0, p.perimeterM, 1e-4)
        assertEquals(listOf(4.0, 3.0, 4.0, 3.0), p.wallLengths.map { Math.round(it * 1e4) / 1e4 })
        p.interiorAnglesDeg.forEach { assertEquals(90.0, it, 1e-3) }
        assertTrue(p.isRectilinear)
        assertEquals(-1.4, p.floorY, 1e-5)
        assertEquals(setOf(RoomWarning.NO_CEILING), p.warnings)
        assertNull(p.volumeM3)
    }

    @Test fun clockwiseIsNormalisedKeepingFirstCorner() {
        val cw = floor(0.0 to 0.0, 0.0 to -3.0, 4.0 to -3.0, 4.0 to 0.0)
        val p = RoomPlan.fromCorners(cw)
        assertEquals(listOf(0, 3, 2, 1), p.sourceIndex)
        assertTrue(PolygonMath.signedArea(p.corners) > 0)
        assertEquals(12.0, p.areaM2, 1e-4)
        // Wall 0 now runs tap 0 -> tap 3.
        assertEquals(0 to 3, p.wallSources(0))
    }

    @Test fun firstCornerAtOriginAndLongestWallHorizontal() {
        // Rotated 30° and offset: the 4 m wall must come out along +x.
        val a = Math.toRadians(30.0)
        val pts = listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0).map { (x, y) ->
            val rx = x * Math.cos(a) - y * Math.sin(a) + 2.0
            val ry = x * Math.sin(a) + y * Math.cos(a) - 5.0
            Vec3(rx.toFloat(), 0f, (-ry).toFloat())
        }
        val p = RoomPlan.fromCorners(pts)
        assertEquals(0.0, p.corners[0].x, 1e-5)
        assertEquals(0.0, p.corners[0].y, 1e-5)
        assertEquals(4.0, p.corners[1].x, 1e-4)
        assertEquals(0.0, p.corners[1].y, 1e-4)
        assertEquals(3.0, p.corners[2].y, 1e-4) // interior above the longest wall (CCW)
    }

    @Test fun lShapeIsRectilinearWithReflexCorner() {
        val l = floor(0.0 to 0.0, 4.0 to 0.0, 4.0 to -2.0, 2.0 to -2.0, 2.0 to -4.0, 0.0 to -4.0)
        val p = RoomPlan.fromCorners(l)
        assertTrue(p.isValid)
        assertEquals(12.0, p.areaM2, 1e-4)
        assertEquals(720.0, p.interiorAnglesDeg.sum(), 1e-3)
        assertEquals(1, p.interiorAnglesDeg.count { kotlin.math.abs(it - 270.0) < 1e-3 })
        assertTrue(p.isRectilinear)
    }

    @Test fun irregularRoomNotRectilinear() {
        val p = RoomPlan.fromCorners(floor(0.0 to 0.0, 4.0 to 0.0, 3.5 to -3.0, 0.0 to -3.0))
        assertTrue(p.isValid)
        assertFalse(p.isRectilinear)
        assertEquals(360.0, p.interiorAnglesDeg.sum(), 1e-3)
    }

    @Test fun nearlySquareWithinToleranceIsRectilinear() {
        // ~2° off: 4 m wall, top shifted 0.1 m over 3 m.
        val p = RoomPlan.fromCorners(floor(0.0 to 0.0, 4.0 to 0.0, 4.1 to -3.0, 0.1 to -3.0))
        assertTrue(p.isRectilinear)
    }

    @Test fun bowTieIsSelfIntersecting() {
        val p = RoomPlan.fromCorners(floor(0.0 to 0.0, 4.0 to -3.0, 4.0 to 0.0, 0.0 to -3.0))
        assertTrue(RoomWarning.SELF_INTERSECTING in p.warnings)
        assertFalse(p.isValid)
        assertFalse(p.isRectilinear)
    }

    @Test fun duplicateTapIsDegenerate() {
        val p = RoomPlan.fromCorners(floor(0.0 to 0.0, 4.0 to 0.0, 4.0 to 0.0, 0.0 to -3.0))
        assertTrue(RoomWarning.DEGENERATE in p.warnings)
        assertFalse(p.isValid)
    }

    @Test fun tooFewCorners() {
        val p = RoomPlan.fromCorners(floor(0.0 to 0.0, 2.0 to 0.0))
        assertTrue(RoomWarning.TOO_FEW_CORNERS in p.warnings)
        assertEquals(listOf(2.0), p.wallLengths.map { Math.round(it * 1e4) / 1e4 })
        assertFalse(p.isValid)
        assertTrue(RoomPlan.fromCorners(emptyList()).corners.isEmpty())
    }

    @Test fun unevenFloorThreshold() {
        val ok = ccwRoom.mapIndexed { i, v -> if (i == 2) v.copy(y = v.y + 0.04f) else v }
        assertFalse(RoomWarning.UNEVEN_FLOOR in RoomPlan.fromCorners(ok).warnings)
        // 5 m diagonal -> tolerance max(5 cm, 15 cm) = 15 cm.
        val big = ccwRoom.mapIndexed { i, v -> if (i == 2) v.copy(y = v.y + 0.12f) else v }
        assertFalse(RoomWarning.UNEVEN_FLOOR in RoomPlan.fromCorners(big).warnings)
        val bad = ccwRoom.mapIndexed { i, v -> if (i == 2) v.copy(y = v.y + 0.2f) else v }
        val p = RoomPlan.fromCorners(bad)
        assertTrue(RoomWarning.UNEVEN_FLOOR in p.warnings)
        assertTrue(p.isValid) // flagged, not rejected
        val small = floor(0.0 to 0.0, 1.0 to 0.0, 1.0 to -1.0).mapIndexed { i, v -> if (i == 1) v.copy(y = v.y + 0.06f) else v }
        assertTrue(RoomWarning.UNEVEN_FLOOR in RoomPlan.fromCorners(small).warnings)
    }

    @Test fun ceilingHeightAndVolume() {
        val p = RoomPlan.fromCorners(ccwRoom, ceiling = Vec3(1f, -1.4f + 2.5f, -1f))
        assertEquals(2.5, p.ceilingHeightM!!, 1e-5)
        assertEquals(30.0, p.volumeM3!!, 1e-3)
        assertFalse(RoomWarning.NO_CEILING in p.warnings)
        val low = p.withCeiling(Vec3(0f, -1.2f, 0f))
        assertNull(low.ceilingHeightM)
        assertTrue(RoomWarning.CEILING_TOO_LOW in low.warnings)
        assertNull(low.volumeM3)
        assertEquals(2.4, p.withCeilingHeight(2.4).ceilingHeightM!!, 1e-9)
    }
}
