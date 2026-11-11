package app.openmeasure.measure.model

import app.openmeasure.measure.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTest {
    private val eps = 1e-6
    private fun p(id: Long, x: Float, y: Float = 0f, z: Float = 0f, c: Float? = null) =
        Point(PointId(id), Vec3(x, y, z), c)

    @Test
    fun segmentLength_3_4_5() {
        val s = Segment(p(1, 0f), p(2, 3f, 4f))
        assertEquals(5.0, s.lengthMeters, eps)
        assertFalse(s.isDegenerate)
    }

    @Test
    fun segmentLength_farFromOrigin_isPrecise() {
        // 1 m line 500 m from the origin: float subtraction must not lose cm precision.
        val s = Segment(p(1, 500f, 0f, 500f), p(2, 501f, 0f, 500f))
        assertEquals(1.0, s.lengthMeters, 1e-4)
    }

    @Test
    fun coincidentPoints_zeroLengthAndDegenerate() {
        val s = Segment(p(1, 1f, 2f, 3f), p(2, 1f, 2f, 3f))
        assertEquals(0.0, s.lengthMeters, 0.0)
        assertTrue(s.isDegenerate)
    }

    @Test
    fun segmentConfidence_isMinOfKnown() {
        assertEquals(0.3f, Segment(p(1, 0f, c = 0.3f), p(2, 1f, c = 0.9f)).confidence!!, 0f)
        assertEquals(0.9f, Segment(p(1, 0f), p(2, 1f, c = 0.9f)).confidence!!, 0f)
        assertNull(Segment(p(1, 0f), p(2, 1f)).confidence)
    }

    @Test
    fun emptyAndSinglePoint_haveNoSegments() {
        assertEquals(0, Path.EMPTY.segments.size)
        assertEquals(0.0, Path.EMPTY.totalLengthMeters, 0.0)
        val one = Path(listOf(p(1, 1f)), closed = true)
        assertEquals(0, one.segments.size)
        assertEquals(0.0, one.totalLengthMeters, 0.0)
        assertFalse(one.isEffectivelyClosed)
    }

    @Test
    fun openPath_multiSegmentTotalsAndRunning() {
        val path = Path(listOf(p(1, 0f), p(2, 1f), p(3, 1f, 2f), p(4, 1f, 2f, 0.5f)))
        assertEquals(3, path.segments.size)
        assertEquals(listOf(1.0, 2.0, 0.5), path.segments.map { it.lengthMeters })
        assertEquals(3.5, path.totalLengthMeters, eps)
        assertEquals(listOf(1.0, 3.0, 3.5), path.cumulativeLengthsMeters)
    }

    @Test
    fun closedPath_addsClosingSegment() {
        val square = Path(listOf(p(1, 0f), p(2, 1f), p(3, 1f, 0f, 1f), p(4, 0f, 0f, 1f)), closed = true)
        assertTrue(square.isEffectivelyClosed)
        assertEquals(4, square.segments.size)
        assertEquals(4.0, square.totalLengthMeters, eps)
    }

    @Test
    fun closedWithTwoPoints_doesNotDoubleCount() {
        val path = Path(listOf(p(1, 0f), p(2, 1f)), closed = true)
        assertEquals(1, path.segments.size)
        assertEquals(1.0, path.totalLengthMeters, eps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun confidenceOutOfRange_rejected() {
        p(1, 0f, c = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFinitePosition_rejected() {
        p(1, Float.NaN)
    }
}
