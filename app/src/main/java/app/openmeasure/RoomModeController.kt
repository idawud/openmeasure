package app.openmeasure

import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.label.MeasureLabel
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.room.RoomLabel
import app.openmeasure.measure.room.RoomLabels
import app.openmeasure.measure.room.RoomPlan
import kotlin.math.hypot

/** F15 steps: tap floor corners, then (optionally) one ceiling point, then the plan is done. */
enum class RoomStep { CORNERS, CEILING, DONE }

/** A closed room: plan + gated labels, plus the world corners (tap order) for saving. */
data class RoomResult(val plan: RoomPlan, val labels: RoomLabels, val corners: List<Vec3>)

/**
 * Room overlay: [preview] = live plan (corners + reticle while scanning) for the mini map; [result] once closed;
 * [edges] = unlabelled dashed closing wall while scanning; [nearFirst] = Place would close the room.
 */
data class RoomUi(
    val step: RoomStep,
    val cornerCount: Int,
    val canClose: Boolean,
    val nearFirst: Boolean,
    val preview: RoomPlan?,
    val result: RoomResult?,
    val edges: List<ScreenSegment> = emptyList(),
)

internal class RoomFrame(val segments: List<SegmentUi>, val ui: RoomUi)

/**
 * F15 room mode. Corners, anchors, snapping, drag and undo are the shared path machinery; this class only knows
 * where the corners end ([closedAt]) and whether the ceiling step was skipped. Placing within [CLOSE_RADIUS_M]
 * of the first corner (with >= 3 corners) closes the room instead of adding a point. Wall labels use the
 * horizontal (floor-plane) length so they match the 2D plan exactly.
 */
internal class RoomModeController {
    var active = false
    /** Number of floor corners once closed; null while scanning. */
    private var closedAt: Int? = null
    private var ceilingSkipped = false

    enum class PlaceAction { ADD, CLOSE, NEW_ROOM }

    fun reset() { closedAt = null; ceilingSkipped = false }

    fun step(n: Int): RoomStep = closedAt?.let { if (n > it || ceilingSkipped) RoomStep.DONE else RoomStep.CEILING } ?: RoomStep.CORNERS

    /** Removing a corner (long-press / undo) below the closed count reopens the room. */
    fun sync(n: Int) { closedAt?.let { if (n < it) reset() } }

    fun close(n: Int) { if (closedAt == null && n >= MIN_CORNERS) closedAt = n }

    fun skipCeiling() { if (closedAt != null) ceilingSkipped = true }

    /** Undo of the room-only steps (skip, close); false = let the session undo the last point. */
    fun undo(n: Int): Boolean {
        val c = closedAt ?: return false
        if (n != c) return false
        if (ceilingSkipped) ceilingSkipped = false else closedAt = null
        return true
    }

    val canUndo: Boolean get() = closedAt != null

    fun placeAction(points: List<Point>, pos: Vec3): PlaceAction = when (step(points.size)) {
        RoomStep.CORNERS -> if (points.size >= MIN_CORNERS && horizontal(pos, points[0].position) < CLOSE_RADIUS_M) PlaceAction.CLOSE else PlaceAction.ADD
        RoomStep.CEILING -> PlaceAction.ADD
        RoomStep.DONE -> PlaceAction.NEW_ROOM
    }

    fun build(points: List<Point>, live: HitResult, observe: (Int) -> EndpointObservation, f: FrameView): RoomFrame {
        sync(points.size)
        val step = step(points.size)
        val k = closedAt ?: points.size
        val corners = points.take(k).map { it.position }
        val obs = (0 until k).map(observe)
        val segs = ArrayList<SegmentUi>()
        if (step == RoomStep.CORNERS) {
            for (i in 0 until k - 1) segs += wall(corners[i], corners[i + 1], obs[i], obs[i + 1], f)
            val lp = live.position
            val near = lp != null && k >= MIN_CORNERS && horizontal(lp, corners[0]) < CLOSE_RADIUS_M
            if (k > 0 && lp != null) segs += wall(corners[k - 1], if (near) corners[0] else lp, obs[k - 1], EndpointObservation.from(live), f, true)
            val tail = if (lp != null && !near) lp else corners.lastOrNull()
            val edges = if (k >= 2 && tail != null) listOfNotNull(f.segment(tail, corners[0])) else emptyList()
            val previewPts = if (lp != null && !near) corners + lp else corners
            val preview = if (previewPts.size >= 2) RoomPlan.fromCorners(previewPts) else null
            return RoomFrame(segs, RoomUi(step, k, k >= MIN_CORNERS, near, preview, null, edges))
        }
        val ceilingIdx = k.takeIf { it < points.size }
        val ceiling = ceilingIdx?.let { points[it].position }
        val plan = RoomPlan.fromCorners(corners, ceiling)
        val labels = RoomLabel.build(f.status, obs, plan, ceilingIdx?.let(observe))
        for (i in 0 until k) {
            val j = (i + 1) % k
            val w = plan.wallLengths.indices.firstOrNull { plan.wallSources(it).let { (a, b) -> (a == i && b == j) || (a == j && b == i) } }
            val label = w?.let { labels.walls[it] } ?: MeasureLabel.build(f.status, obs[i], obs[j], horizontal(corners[i], corners[j]))
            segs += SegmentUi(f.segment(corners[i], corners[j]), label, false)
        }
        if (step == RoomStep.CEILING) live.position?.let { lp ->
            val foot = Vec3(lp.x, plan.floorY.toFloat(), lp.z)
            val h = lp.y - plan.floorY
            // Height depends on every corner (floor level) and the reticle: gate by the weakest of them.
            val r = RoomLabel.weakest(f.status, obs + EndpointObservation.from(live))
            val label = when {
                !r.canDisplayNumber -> LabelContent.Hint(r.reason)
                h < RoomPlan.MIN_CEILING_M -> LabelContent.Hint(ConfidenceReason.NO_HIT)
                else -> LabelContent.Length(h, r.level, r.score)
            }
            segs += SegmentUi(f.segment(foot, lp), label, true)
        }
        if (ceiling != null) {
            val foot = Vec3(ceiling.x, plan.floorY.toFloat(), ceiling.z)
            labels.ceiling?.let { segs += SegmentUi(f.segment(foot, ceiling), it, false) }
        }
        return RoomFrame(segs, RoomUi(step, k, false, false, plan, RoomResult(plan, labels, corners)))
    }

    private fun wall(a: Vec3, b: Vec3, oa: EndpointObservation, ob: EndpointObservation, f: FrameView, live: Boolean = false): SegmentUi {
        val label: LabelContent = MeasureLabel.build(f.status, oa, ob, horizontal(a, b))
        return SegmentUi(f.segment(a, b), label, live)
    }

    companion object {
        const val MIN_CORNERS = 3
        /** Place within this horizontal distance of the first corner closes the room. */
        const val CLOSE_RADIUS_M = 0.15f

        fun horizontal(a: Vec3, b: Vec3): Double = hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())
    }
}
