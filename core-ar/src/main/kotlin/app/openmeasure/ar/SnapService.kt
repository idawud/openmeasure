package app.openmeasure.ar

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenProjection
import app.openmeasure.measure.snap.EdgeSnapper
import app.openmeasure.measure.snap.SnapHysteresis
import app.openmeasure.measure.snap.SnapKind
import app.openmeasure.measure.snap.SnapTarget
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Thin ARCore adapter for edge/corner snapping (F8): Frame -> DepthMap -> [EdgeSnapper] -> [SnapHysteresis].
 * GL thread only. Pass the same [FrameDepthSource] as [HitTestService] so depth is copied once per frame.
 */
class SnapService(
    private val depthSource: FrameDepthSource,
    private val snapper: EdgeSnapper = EdgeSnapper(),
) {
    /** [hit] is the snapped hit (or [base] unchanged); [screen] is where to draw the snap indicator. */
    class Outcome(val hit: ArHit, val kind: SnapKind, val acquired: Boolean, val screen: ScreenPoint?)

    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)

    /**
     * Snaps [base] (the normal hit at view pixel [x],[y]) to a nearby edge/corner. The snapped point is never
     * more than [maxRadiusPx] view pixels from ([x],[y]) when acquired, never on low-confidence depth, and
     * only while tracking with a valid base hit; otherwise [base] is returned unchanged.
     */
    fun snap(
        frame: Frame, x: Float, y: Float, base: ArHit, hysteresis: SnapHysteresis,
        viewWidth: Int, viewHeight: Int, maxRadiusPx: Float,
    ): Outcome {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING || !base.result.isHit || viewWidth <= 0 || viewHeight <= 0) {
            hysteresis.reset()
            return Outcome(base, SnapKind.NONE, false, null)
        }
        camera.getViewMatrix(viewM, 0)
        camera.getProjectionMatrix(projM, 0, NEAR, FAR)
        val w = viewWidth.toFloat()
        val h = viewHeight.toFloat()
        val project = { p: Vec3 -> ScreenProjection.project(p, viewM, projM, w, h) }
        val reticle = ScreenPoint(x, y)
        val camPose = camera.pose.toPose()

        val fresh = detect(frame, reticle, maxRadiusPx, camPose, project)
        val update = hysteresis.update(reticle, fresh, project)
        val t = update.target ?: return Outcome(base, SnapKind.NONE, false, null)
        val result = HitResult(
            HitSource.DEPTH, t.position, Pose(t.position), null, t.confidence,
            camPose.translation.distanceTo(t.position),
        )
        return Outcome(ArHit(result, null), t.kind, update.acquired, project(t.position))
    }

    private fun detect(
        frame: Frame, reticle: ScreenPoint, maxRadiusPx: Float, camPose: Pose, project: (Vec3) -> ScreenPoint?,
    ): SnapTarget? {
        val fd = depthSource.get(frame) ?: return null
        val (u, v) = depthSource.viewToDepthPixel(frame, fd, reticle.x, reticle.y)
        // View radius -> depth pixels (depth maps are ~10x coarser than the screen); at least 2 so the
        // detector has room, the screen-space check below enforces the real cap.
        val (ux, vx) = depthSource.viewToDepthPixel(frame, fd, reticle.x + maxRadiusPx, reticle.y)
        val (uy, vy) = depthSource.viewToDepthPixel(frame, fd, reticle.x, reticle.y + maxRadiusPx)
        val radius = ceil(max(hypot(ux - u, vx - v), hypot(uy - u, vy - v))).toInt().coerceIn(2, MAX_DEPTH_RADIUS)
        val target = snapper.snap(fd.map, fd.intrinsics, u, v, radius, camPose).toTarget() ?: return null
        val s = project(target.position) ?: return null
        return if (hypot(s.x - reticle.x, s.y - reticle.y) <= maxRadiusPx) target else null
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
        const val MAX_DEPTH_RADIUS = 12
    }
}
