package app.openmeasure.measure.height

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHit
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap

/** Why a height is (not) shown, in check order. Only [OK] carries a number. */
enum class HeightReason {
    TRACKING_POOR,
    NO_PERSON,
    PARTIAL_BODY,
    LOW_DEPTH_CONFIDENCE,
    TOO_CLOSE,
    TOO_FAR,
    NOT_UPRIGHT,
    NO_FLOOR,
    OK,
}

enum class FloorSource {
    /** ARCore horizontal plane under the feet (preferred, ~1 cm). */
    PLANE,
    /** Depth at heels/toes/ankles minus a fixed landmark-above-floor offset (~2-3 cm worse). */
    DEPTH,
}

/** Per-frame estimate. [heightMeters] is non-null iff [reason] == OK (and then [confidenceLevel] != NONE). */
data class HeightResult(
    val heightMeters: Float?,
    val confidenceLevel: ConfidenceLevel,
    val reason: HeightReason,
    val score: Float = 0f,
    /** Horizontal camera-to-person distance, when known. */
    val distanceM: Float? = null,
    val floorSource: FloorSource? = null,
) {
    val isOk: Boolean get() = reason == HeightReason.OK && heightMeters != null

    companion object {
        fun fail(reason: HeightReason, distanceM: Float? = null) =
            HeightResult(null, ConfidenceLevel.NONE, reason, 0f, distanceM)
    }
}

/** Depth query in camera (CPU) image pixel coordinates -> world point, or null when no reliable depth. */
fun interface DepthLookup {
    fun pointAt(u: Float, v: Float): DepthHit?
}

/**
 * [DepthLookup] over an ARCore depth map. The depth image covers the same field of view as the CPU image,
 * so CPU pixel -> normalised (0..1) -> depth pixel; no intrinsics guessing about aspect ratios.
 */
class DepthMapLookup(
    private val map: DepthMap,
    /** Intrinsics scaled to the depth map resolution. */
    private val depthIntrinsics: CameraIntrinsics,
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val cameraPose: Pose,
    private val tester: DepthHitTester = HEIGHT_DEPTH_TESTER,
) : DepthLookup {
    override fun pointAt(u: Float, v: Float): DepthHit? {
        val nx = (u + 0.5f) / imageWidth
        val ny = (v + 0.5f) / imageHeight
        if (nx !in 0f..1f || ny !in 0f..1f) return null
        val (du, dv) = depthIntrinsics.normalizedToPixel(nx, ny)
        return tester.hitTest(map, depthIntrinsics, du, dv, cameraPose)
    }

    companion object {
        /**
         * Height is insensitive to depth error along the view ray (only ~sin(elevation) of it reaches the
         * vertical), so confidence decays later than for tape measuring: full to 4 m, zero at 7 m.
         */
        val HEIGHT_DEPTH_TESTER = DepthHitTester(fullConfidenceRangeM = 4f, zeroConfidenceRangeM = 7f)
    }
}

/**
 * Everything the estimator needs from one camera frame (all from the *same* frame as the landmarks).
 * @param intrinsics CPU-image intrinsics (the landmark pixel space).
 * @param cameraPose ARCore `Camera.getPose()` (sensor-aligned, OpenGL axes).
 * @param floorPlanes tracked upward-facing horizontal planes (point + unit normal); may be empty.
 * @param up world gravity-up (ARCore world +Y).
 */
data class HeightFrame(
    val landmarks: PoseLandmarks,
    val intrinsics: CameraIntrinsics,
    val cameraPose: Pose,
    val depth: DepthLookup?,
    val floorPlanes: List<Plane>,
    val tracking: TrackingStatus = TrackingStatus.TRACKING,
    val up: Vec3 = Vec3(0f, 1f, 0f),
)
