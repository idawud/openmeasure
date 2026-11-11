package app.openmeasure

import app.openmeasure.measure.box.BoxFit
import app.openmeasure.measure.box.BoxLabel
import app.openmeasure.measure.box.BoxWarning
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.label.MeasureLabel
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenSegment

/** Guided F14 steps; derived from the number of placed points (0..4). */
enum class BoxStep { PLACE_A, PLACE_B, PLACE_C, PLACE_TOP, DONE }

/**
 * Box overlay: [edges] = base rectangle (4) or full wireframe (12), near-plane clipped, unlabelled;
 * [volume] gated by confidence (null until the top is known); [center] = projected box centre.
 */
data class BoxUi(
    val step: BoxStep,
    val edges: List<ScreenSegment> = emptyList(),
    val volume: VolumeContent? = null,
    val center: ScreenPoint? = null,
    val baseNotLevel: Boolean = false,
)

/** Labelled W/D/H segments (drawn + labelled by the shared path renderer) and the box overlay. */
internal class BoxFrame(val segments: List<SegmentUi>, val ui: BoxUi)

/**
 * F14 box mode: A, B along a bottom edge (width), C on the adjacent bottom edge (depth), T on the top (height).
 * Points, anchors, snapping, drag and undo are the shared path machinery; a 5th placed point starts a new box.
 * Until a step is placed, the reticle hit stands in for it (dashed preview).
 */
internal class BoxModeController {
    var active = false

    fun build(points: List<Point>, live: HitResult, observe: (Int) -> EndpointObservation, f: FrameView): BoxFrame {
        val n = minOf(points.size, POINTS)
        val step = BoxStep.entries[n]
        val pos = points.take(n).map { it.position }.toMutableList()
        val obs = (0 until n).map(observe).toMutableList()
        val livePos = live.position
        val liveIdx = if (n < POINTS && livePos != null) n else -1
        if (liveIdx >= 0) { pos += livePos!!; obs += EndpointObservation.from(live) }
        val isLive = { i: Int -> i == liveIdx }
        return when (pos.size) {
            0, 1 -> BoxFrame(emptyList(), BoxUi(step))
            2 -> {
                val label = MeasureLabel.build(f.status, obs[0], obs[1], pos[0].distanceTo(pos[1]).toDouble())
                BoxFrame(listOf(SegmentUi(f.segment(pos[0], pos[1]), label, isLive(1))), BoxUi(step))
            }
            3 -> {
                val base = BoxFit.fitBase(pos[0], pos[1], pos[2])
                val l = BoxLabel.build(f.status, obs, base, null)
                val c = base.corners
                val segs = listOf(SegmentUi(f.segment(c[0], c[1]), l.width, false), SegmentUi(f.segment(c[1], c[2]), l.depth, isLive(2)))
                val edges = (0 until 4).mapNotNull { f.segment(c[it], c[(it + 1) % 4]) }
                BoxFrame(segs, BoxUi(step, edges, baseNotLevel = BoxWarning.BASE_NOT_LEVEL in base.warnings))
            }
            else -> {
                val base = BoxFit.fitBase(pos[0], pos[1], pos[2])
                val box = BoxFit.fit(base, pos[3])
                val l = BoxLabel.build(f.status, obs, base, box)
                val c = box.corners
                // Label the vertical edge nearest the camera so the H label is not hidden behind the box.
                val v = (0 until 4).minBy { f.camPos.distanceTo(c[it]) }
                val segs = listOf(
                    SegmentUi(f.segment(c[0], c[1]), l.width, false),
                    SegmentUi(f.segment(c[1], c[2]), l.depth, false),
                    SegmentUi(f.segment(c[v], c[v + 4]), l.height!!, isLive(3)),
                )
                val edges = BoxFit.EDGES.mapNotNull { (i, j) -> f.segment(c[i], c[j]) }
                val centre = c.reduce { acc, p -> acc + p } * (1f / c.size)
                BoxFrame(segs, BoxUi(step, edges, l.volume, f.project(centre), BoxWarning.BASE_NOT_LEVEL in box.warnings))
            }
        }
    }

    companion object {
        const val POINTS = 4
    }
}
