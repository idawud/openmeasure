package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.model.PointId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementSessionTest {
    private val eps = 1e-6
    private fun v(x: Float, y: Float = 0f, z: Float = 0f) = Vec3(x, y, z)

    @Test
    fun emptySession() {
        val s = MeasurementSession()
        assertTrue(s.state.isEmpty)
        assertEquals(0.0, s.state.totalLengthMeters, 0.0)
        assertFalse(s.state.canUndo)
        assertFalse(s.undo())
    }

    @Test
    fun singlePoint_noLength() {
        val s = MeasurementSession()
        s.addPoint(v(1f))
        assertEquals(1, s.state.points.size)
        assertEquals(0, s.state.segments.size)
        assertEquals(0.0, s.state.totalLengthMeters, 0.0)
    }

    @Test
    fun twoPoints_lengthAndUniqueIds() {
        val s = MeasurementSession()
        val a = s.addPoint(v(0f))
        val b = s.addPoint(v(0f, 0f, 1f), confidence = 0.8f)
        assertNotEquals(a, b)
        assertEquals(1.0, s.state.segments.single().lengthMeters, eps)
        assertEquals(0.8f, s.state.point(b)!!.confidence!!, 0f)
    }

    @Test
    fun coincidentPoints_zeroLength() {
        val s = MeasurementSession()
        s.addPoint(v(1f, 1f, 1f)); s.addPoint(v(1f, 1f, 1f))
        assertEquals(0.0, s.state.totalLengthMeters, 0.0)
        assertTrue(s.state.segments.single().isDegenerate)
    }

    @Test
    fun multiSegmentTotal_andClose() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); s.addPoint(v(1f)); s.addPoint(v(1f, 1f))
        assertEquals(2.0, s.state.totalLengthMeters, eps)
        s.setClosed(true)
        assertEquals(2.0 + Math.sqrt(2.0), s.state.totalLengthMeters, 1e-6)
        assertTrue(s.undo())
        assertFalse(s.state.closed)
        assertEquals(2.0, s.state.totalLengthMeters, eps)
    }

    @Test
    fun move_isUndoable_andDragCoalesces() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); val b = s.addPoint(v(1f))
        s.movePoint(b, v(2f)); s.movePoint(b, v(3f)); s.movePoint(b, v(4f))
        s.endMove()
        assertEquals(4.0, s.state.totalLengthMeters, eps)
        assertTrue(s.undo()) // whole drag reverted in one step
        assertEquals(1.0, s.state.totalLengthMeters, eps)
    }

    @Test
    fun separateDrags_areSeparateUndoSteps() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); val b = s.addPoint(v(1f))
        s.movePoint(b, v(2f)); s.endMove()
        s.movePoint(b, v(3f)); s.endMove()
        s.undo()
        assertEquals(2.0, s.state.totalLengthMeters, eps)
        s.undo()
        assertEquals(1.0, s.state.totalLengthMeters, eps)
    }

    @Test
    fun remove_andUndo_restoresOrder() {
        val s = MeasurementSession()
        val a = s.addPoint(v(0f)); val b = s.addPoint(v(1f)); val c = s.addPoint(v(1f, 1f))
        assertTrue(s.removePoint(b))
        assertEquals(listOf(a, c), s.state.points.map { it.id })
        assertEquals(Math.sqrt(2.0), s.state.totalLengthMeters, 1e-6)
        s.undo()
        assertEquals(listOf(a, b, c), s.state.points.map { it.id })
        assertFalse(s.removePoint(PointId(999)))
    }

    @Test
    fun clear_andUndo() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); s.addPoint(v(1f))
        s.clear()
        assertTrue(s.state.isEmpty)
        assertTrue(s.state.canUndo)
        s.undo()
        assertEquals(1.0, s.state.totalLengthMeters, eps)
    }

    @Test
    fun undoAll_thenNothingLeft() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); s.addPoint(v(1f))
        assertTrue(s.undo()); assertTrue(s.undo())
        assertTrue(s.state.isEmpty)
        assertFalse(s.state.canUndo)
        assertFalse(s.undo())
    }

    @Test
    fun liveUpdate_recomputesAndIsNotUndoable() {
        val s = MeasurementSession()
        val a = s.addPoint(v(0f)); val b = s.addPoint(v(1f))
        assertTrue(s.updatePoint(b, v(1.01f)))
        assertEquals(1.01, s.state.totalLengthMeters, 1e-6)
        s.undo() // undoes addPoint(b), not the refinement
        assertEquals(listOf(a), s.state.points.map { it.id })
        assertFalse(s.updatePoint(b, v(5f)))
    }

    @Test
    fun liveUpdate_propagatesIntoHistory() {
        val s = MeasurementSession()
        s.addPoint(v(0f)); val b = s.addPoint(v(1f))
        s.movePoint(b, v(2f)); s.endMove()
        s.updatePoint(b, v(2f, 0.1f)) // anchor drift correction of +0.1 y
        s.undo() // revert the user's move: drift correction must survive
        assertEquals(v(1f, 0.1f), s.state.point(b)!!.position)
    }

    @Test
    fun batchUpdate_publishesOnce() {
        val s = MeasurementSession()
        val a = s.addPoint(v(0f)); val b = s.addPoint(v(1f))
        var calls = 0
        s.listener = { calls++ }
        s.updatePoints(mapOf(a to v(0f, 1f), b to v(2f, 1f), PointId(42) to v(9f)))
        assertEquals(1, calls)
        assertEquals(2.0, s.state.totalLengthMeters, eps)
        s.updatePoints(mapOf(a to v(0f, 1f)))
        assertEquals(1, calls) // unchanged -> no publish
    }

    @Test
    fun listener_andRevision_trackChanges() {
        var last: MeasurementState? = null
        val s = MeasurementSession(listener = { last = it })
        s.addPoint(v(0f))
        val r1 = s.state.revision
        s.addPoint(v(1f))
        assertTrue(s.state.revision > r1)
        assertEquals(s.state, last)
    }

    @Test
    fun undoHistory_isBounded() {
        val s = MeasurementSession(maxUndo = 3)
        repeat(10) { s.addPoint(v(it.toFloat())) }
        var n = 0
        while (s.undo()) n++
        assertEquals(3, n)
        assertEquals(7, s.state.points.size)
    }

    @Test
    fun addPointsIsOneUndoStep() {
        val s = MeasurementSession()
        s.addPoint(v(9f))
        val ids = s.addPoints(listOf(v(0f), v(1f), v(1f, 1f), v(0f, 1f)), 0.8f)
        assertEquals(4, ids.toSet().size)
        assertEquals(5, s.state.points.size)
        assertEquals(0.8f, s.state.points.last().confidence)
        assertTrue(s.undo())
        assertEquals(1, s.state.points.size)
        assertTrue(s.addPoints(emptyList()).isEmpty())
        assertEquals(1, s.state.points.size)
    }
}
