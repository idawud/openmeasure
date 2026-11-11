package app.openmeasure

import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.label.MeasureLabel
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenProjection
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.rect.QuadAnalyzer
import app.openmeasure.measure.rect.RectLabel

/** One frame's camera matrices and tracking state, for projecting world points to view pixels. */
internal class FrameView(
    val viewM: FloatArray,
    val projM: FloatArray,
    val width: Float,
    val height: Float,
    val camPos: Vec3,
    val status: TrackingStatus,
) {
    fun project(p: Vec3): ScreenPoint? = ScreenProjection.project(p, viewM, projM, width, height)
    fun segment(a: Vec3, b: Vec3): ScreenSegment? = ScreenProjection.projectSegment(a, b, viewM, projM, width, height)
}

/** Segments to draw plus the placed (non-live) labels, for the path total. */
internal class FrameSegments(val segments: List<SegmentUi>, val placed: List<LabelContent>, val rect: RectUi? = null)

/**
 * Builds the drawable segments for a frame. [observe] gives the endpoint observation for point index i.
 * Path mode: placed segments + a live segment to the reticle. Rectangle mode: the same until 4 corners exist,
 * then the closed quad with per-side labels and an area label (via [QuadAnalyzer] + [RectLabel]).
 */
internal object MeasureFrame {
    private val analyzer = QuadAnalyzer()

    fun build(
        points: List<Point>,
        finished: Boolean,
        rectMode: Boolean,
        live: HitResult,
        observe: (Int) -> EndpointObservation,
        f: FrameView,
    ): FrameSegments {
        if (rectMode && points.size == RECT_CORNERS) return rect(points, observe, f)
        val segs = ArrayList<SegmentUi>()
        val placed = ArrayList<LabelContent>()
        for (i in 0 until points.size - 1) {
            val a = points[i].position
            val b = points[i + 1].position
            val label = MeasureLabel.build(f.status, observe(i), observe(i + 1), a.distanceTo(b).toDouble())
            placed += label
            segs += SegmentUi(f.segment(a, b), label, false)
        }
        if (points.isNotEmpty() && !finished) {
            val last = points.size - 1
            val start = points[last].position
            val end = live.position
            segs += if (end != null) {
                SegmentUi(
                    f.segment(start, end),
                    MeasureLabel.build(f.status, observe(last), EndpointObservation.from(live), start.distanceTo(end).toDouble()),
                    true,
                )
            } else {
                SegmentUi(null, MeasureLabel.build(f.status, observe(last), EndpointObservation.NONE, 0.0), true)
            }
        }
        return FrameSegments(segs, placed)
    }

    private fun rect(points: List<Point>, observe: (Int) -> EndpointObservation, f: FrameView): FrameSegments {
        val result = analyzer.analyze(points.map { it.position }, reorder = true)
        val segs = ArrayList<SegmentUi>(RECT_CORNERS)
        if (result.isValid) {
            val labels = RectLabel.build(f.status, result.order.map(observe), result)
            for (i in 0 until RECT_CORNERS) {
                val a = result.corners[i]
                val b = result.corners[(i + 1) % RECT_CORNERS]
                segs += SegmentUi(f.segment(a, b), labels.sides[i], false)
            }
            val c = result.corners.reduce { acc, p -> acc + p } * (1f / RECT_CORNERS)
            return FrameSegments(segs, emptyList(), RectUi(labels.area, f.project(c), result.warnings))
        }
        // Degenerate (bow-tie, collinear): draw the taps as placed so the user can see what to fix.
        for (i in 0 until RECT_CORNERS) {
            val j = (i + 1) % RECT_CORNERS
            val a = points[i].position
            val b = points[j].position
            segs += SegmentUi(f.segment(a, b), MeasureLabel.build(f.status, observe(i), observe(j), a.distanceTo(b).toDouble()), false)
        }
        val anchor = points.firstNotNullOfOrNull { f.project(it.position) }
        return FrameSegments(segs, emptyList(), RectUi(AreaContent.Invalid, anchor, result.warnings))
    }

    const val RECT_CORNERS = 4
}
