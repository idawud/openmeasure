package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.model.Path
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.model.Segment

/**
 * Immutable snapshot of a measurement, suitable as the value of a StateFlow.
 * [revision] increases on every change so observers can cheaply detect updates.
 */
data class MeasurementState(
    val path: Path = Path.EMPTY,
    val canUndo: Boolean = false,
    val revision: Long = 0,
) {
    val points: List<Point> get() = path.points
    val segments: List<Segment> get() = path.segments
    val totalLengthMeters: Double get() = path.totalLengthMeters
    val closed: Boolean get() = path.closed
    val isEmpty: Boolean get() = path.points.isEmpty()

    fun point(id: PointId): Point? = path.points.firstOrNull { it.id == id }
}

/**
 * Mutable holder of an in-progress measurement (F1 point-to-point, F3 multi-segment path, F4 edit).
 *
 * Undo semantics:
 * - [addPoint], [removePoint], [movePoint], [setClosed], [clear] are user edits and are undoable.
 * - Consecutive [movePoint] calls on the same point (a drag) coalesce into ONE undo step; call
 *   [endMove] when the finger lifts so the next drag of that point becomes its own step.
 * - [updatePoint] is tracking refinement (anchor pose changed), NOT a user edit: it is not undoable.
 *   The same displacement is applied to that point in the undo history, so undoing later does not
 *   resurrect pre-refinement (drifted) coordinates.
 *
 * Mutators are synchronised (UI thread taps vs. GL-thread anchor updates). After each change [state]
 * holds the new snapshot and [listener] is called with it (e.g. `listener = { flow.value = it }`).
 */
class MeasurementSession(
    private val maxUndo: Int = 100,
    var listener: ((MeasurementState) -> Unit)? = null,
) {
    private data class Snapshot(val points: List<Point>, val closed: Boolean)

    private var points: List<Point> = emptyList()
    private var closed: Boolean = false
    private val history = ArrayDeque<Snapshot>()
    private var nextId: Long = 1
    private var revision: Long = 0
    private var draggingId: PointId? = null

    @Volatile
    var state: MeasurementState = MeasurementState()
        private set

    /** Appends a point to the end of the path and returns its id. */
    @Synchronized
    fun addPoint(position: Vec3, confidence: Float? = null): PointId {
        val p = Point(PointId(nextId++), position, confidence)
        pushHistory()
        points = points + p
        publish()
        return p.id
    }

    /** Appends several points as ONE undo step (e.g. auto-detected rectangle corners); returns their ids. */
    @Synchronized
    fun addPoints(positions: List<Vec3>, confidence: Float? = null): List<PointId> {
        if (positions.isEmpty()) return emptyList()
        val added = positions.map { Point(PointId(nextId++), it, confidence) }
        pushHistory()
        points = points + added
        publish()
        return added.map { it.id }
    }

    /** Removes [id]; returns false (and records nothing) if it does not exist. */
    @Synchronized
    fun removePoint(id: PointId): Boolean {
        if (indexOf(id) < 0) return false
        pushHistory()
        points = points.filterNot { it.id == id }
        publish()
        return true
    }

    /**
     * User drag of a point to [position]. Undoable; consecutive moves of the same point coalesce.
     * [confidence] null keeps the point's previous confidence.
     */
    @Synchronized
    fun movePoint(id: PointId, position: Vec3, confidence: Float? = null): Boolean {
        val i = indexOf(id)
        if (i < 0) return false
        if (draggingId != id) {
            pushHistory()
            draggingId = id
        }
        val old = points[i]
        points = points.replaced(i, old.copy(position = position, confidence = confidence ?: old.confidence))
        publish()
        return true
    }

    /** Ends the current drag so the next [movePoint] starts a new undo step. */
    @Synchronized
    fun endMove() {
        draggingId = null
    }

    /**
     * Live update from tracking: the anchor backing [id] now resolves to [position].
     * Not undoable. Returns false if [id] is unknown. No-op publish if nothing changed.
     */
    @Synchronized
    fun updatePoint(id: PointId, position: Vec3, confidence: Float? = null): Boolean {
        val i = indexOf(id)
        if (i < 0) return false
        val old = points[i]
        val newConf = confidence ?: old.confidence
        if (old.position == position && old.confidence == newConf) return true
        val delta = position - old.position
        points = points.replaced(i, old.copy(position = position, confidence = newConf))
        shiftInHistory(id, delta)
        publish()
        return true
    }

    /** Batch live update (one publish per AR frame). Unknown ids are ignored. */
    @Synchronized
    fun updatePoints(positions: Map<PointId, Vec3>) {
        var changed = false
        points = points.map { p ->
            val np = positions[p.id]
            if (np == null || np == p.position) p
            else {
                shiftInHistory(p.id, np - p.position)
                changed = true
                p.copy(position = np)
            }
        }
        if (changed) publish()
    }

    /** Closes/opens the path (closing segment exists only with >= 3 points). */
    @Synchronized
    fun setClosed(value: Boolean) {
        if (closed == value) return
        pushHistory()
        closed = value
        publish()
    }

    /** Removes all points. Undoable (unless already empty). */
    @Synchronized
    fun clear() {
        if (points.isEmpty() && !closed) return
        pushHistory()
        points = emptyList()
        closed = false
        publish()
    }

    /** Reverts the last user edit. Returns false if there is nothing to undo. */
    @Synchronized
    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        points = s.points
        closed = s.closed
        draggingId = null
        publish()
        return true
    }

    private fun indexOf(id: PointId) = points.indexOfFirst { it.id == id }

    private fun pushHistory() {
        draggingId = null
        history.addLast(Snapshot(points, closed))
        while (history.size > maxUndo) history.removeFirst()
    }

    private fun shiftInHistory(id: PointId, delta: Vec3) {
        for (h in history.indices) {
            val s = history[h]
            val i = s.points.indexOfFirst { it.id == id }
            if (i >= 0) {
                val p = s.points[i]
                history[h] = s.copy(points = s.points.replaced(i, p.copy(position = p.position + delta)))
            }
        }
    }

    private fun publish() {
        revision++
        val s = MeasurementState(Path(points, closed), history.isNotEmpty(), revision)
        state = s
        listener?.invoke(s)
    }

    private fun <T> List<T>.replaced(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
}
