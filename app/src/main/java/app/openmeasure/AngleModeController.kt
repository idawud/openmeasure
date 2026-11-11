package app.openmeasure

import app.openmeasure.measure.angle.AngleContent
import app.openmeasure.measure.angle.AngleLabel
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.projection.ScreenPoint

/** Angle overlay data: [content] gated by confidence; screen positions of the vertex and the two arm ends (null = behind camera). */
data class AngleUi(val content: AngleContent?, val vertex: ScreenPoint?, val a: ScreenPoint?, val b: ScreenPoint?)

/**
 * F16 angle mode: the user places A (arm end), V (vertex), B (arm end); the angle is at V. A 4th point starts a new angle.
 * Only the mode flag and the per-frame label live here; points, anchors, drag and undo are the shared path machinery.
 */
internal class AngleModeController {
    var active = false

    /** Angle UI once all 3 points exist, else null. */
    fun build(points: List<Point>, observe: (Int) -> EndpointObservation, f: FrameView): AngleUi? {
        if (!active || points.size < POINTS) return null
        val (a, v, b) = points.take(POINTS).map { it.position }
        val content = AngleLabel.build(f.status, observe(0), observe(1), observe(2), a, v, b)
        return AngleUi(content, f.project(v), f.project(a), f.project(b))
    }

    companion object {
        const val POINTS = 3
    }
}
