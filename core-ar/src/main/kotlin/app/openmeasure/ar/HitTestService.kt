package app.openmeasure.ar

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Quaternion
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.DepthHit
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.HitResolver
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.PlaneHit
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.HitResult as ArCoreHitResult
import com.google.ar.core.Pose as ArPose

/** Engine result plus the ARCore plane hit it came from (if any). */
class ArHit(val result: HitResult, val planeHit: ArCoreHitResult?) {
    /** Same hit with the position replaced (e.g. temporally smoothed); keeps source, rotation and confidence. */
    fun withPosition(p: Vec3): ArHit = if (!result.isHit) this else ArHit(
        result.copy(position = p, pose = result.pose?.copy(translation = p) ?: Pose(p)), planeHit,
    )

    /**
     * Always a world-fixed anchor at the final measured position. Plane-attached anchors were the drift
     * source: ARCore moves them whenever the plane is refined, re-fit or merged (subsumed), so a point placed
     * from a depth-fused/snapped position kept sliding after placement. World anchors only move on genuine
     * map corrections, which [app.openmeasure.measure.session.AnchorPoseFilter] gates.
     */
    fun createAnchor(session: Session): Anchor? {
        val p = result.position ?: return null
        return session.createAnchor(ArPose.makeTranslation(p.x, p.y, p.z))
    }
}

/**
 * Thin ARCore adapter: maps a [Frame] to core-measure inputs and lets [HitResolver] decide.
 * Must be called on the GL thread with the current frame (e.g. from ArCameraView.onFrame).
 */
class HitTestService(
    private val depthTester: DepthHitTester = DepthHitTester(),
    private val resolver: HitResolver = HitResolver(),
    /** Share one instance with [SnapService] so depth is copied once per frame. */
    val depthSource: FrameDepthSource = FrameDepthSource(),
) {
    fun hitTestCenter(frame: Frame, viewWidth: Int, viewHeight: Int): ArHit =
        hitTest(frame, viewWidth / 2f, viewHeight / 2f)

    /** [x], [y] in view pixels (same space as touch events / the GLSurfaceView). */
    fun hitTest(frame: Frame, x: Float, y: Float): ArHit {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return ArHit(HitResult.NONE, null)
        val camPose = camera.pose.toPose()
        val depthHit = depthHit(frame, x, y, camPose)
        val arPlaneHit = planeHit(frame, x, y)
        val result = resolver.resolve(
            isTracking = true,
            cameraPosition = camPose.translation,
            depth = depthHit,
            plane = arPlaneHit?.let { PlaneHit(it.hitPose.toPose()) },
        )
        return ArHit(result, arPlaneHit)
    }

    private fun depthHit(frame: Frame, x: Float, y: Float, camPose: Pose): DepthHit? {
        val fd = depthSource.get(frame) ?: return null
        val (u, v) = depthSource.viewToDepthPixel(frame, fd, x, y)
        return depthTester.hitTest(fd.map, fd.intrinsics, u, v, camPose)
    }

    /** First tracked plane hit whose pose lies inside the plane polygon. */
    private fun planeHit(frame: Frame, x: Float, y: Float): ArCoreHitResult? =
        frame.hitTest(x, y).firstOrNull { hit ->
            val plane = hit.trackable as? Plane ?: return@firstOrNull false
            plane.trackingState == TrackingState.TRACKING &&
                plane.subsumedBy == null &&
                plane.isPoseInPolygon(hit.hitPose)
        }
}

fun ArPose.toPose(): Pose = Pose(Vec3(tx(), ty(), tz()), Quaternion(qx(), qy(), qz(), qw()))

fun Pose.toArPose(): ArPose = ArPose(
    floatArrayOf(translation.x, translation.y, translation.z),
    floatArrayOf(rotation.x, rotation.y, rotation.z, rotation.w),
)
