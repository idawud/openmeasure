package app.openmeasure.ar

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.height.BodyLandmark
import app.openmeasure.measure.height.DepthMapLookup
import app.openmeasure.measure.height.HeightEstimator
import app.openmeasure.measure.height.HeightFrame
import app.openmeasure.measure.height.HeightReason
import app.openmeasure.measure.height.HeightResult
import app.openmeasure.measure.height.HeightSmoother
import app.openmeasure.measure.height.ImageRotation
import app.openmeasure.measure.height.Landmark2D
import app.openmeasure.measure.height.PoseLandmarks
import app.openmeasure.measure.height.SmoothedHeight
import app.openmeasure.measure.hit.CameraIntrinsics
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.google.ar.core.Plane as ArPlane

/** Live height state. [skeletonView] holds bone segments in view pixels: x0,y0,x1,y1, ... (empty = none). */
data class PoseHeightState(
    val smoothed: SmoothedHeight = SmoothedHeight.EMPTY,
    val latest: HeightResult = HeightResult.fail(HeightReason.NO_PERSON),
    val skeletonView: FloatArray = FloatArray(0),
)

/**
 * F10 adapter: ARCore CPU image -> ML Kit pose (STREAM_MODE, base model) -> [HeightEstimator] + [HeightSmoother].
 *
 * Call [onFrame] on the GL thread every frame. At most one detection is in flight and launches are throttled to
 * [minIntervalMs] (10 fps). Everything the estimator needs (depth map copy, camera pose, intrinsics, floor planes)
 * is snapshotted from the *same* frame as the image, so the async result is geometrically consistent.
 * Landmarks come back in the upright (rotated) image and are mapped back to sensor pixels before use.
 */
class PoseHeightService(
    private val context: Context,
    private val depthSource: FrameDepthSource = FrameDepthSource(),
    private val estimator: HeightEstimator = HeightEstimator(),
    private val smoother: HeightSmoother = HeightSmoother(),
    private val minIntervalMs: Long = 100L,
) {
    private val thread = Executors.newSingleThreadExecutor()
    /** Drops work after [close] instead of throwing RejectedExecutionException on ML Kit's threads. */
    private val executor = Executor { r ->
        try { thread.execute(r) } catch (e: RejectedExecutionException) { /* closed */ }
    }
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .setExecutor(executor)
            .build(),
    )
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    private var lastLaunchMs = 0L
    private var nv21: ByteArray? = null
    private var sensorOrientation: Int? = null

    @Volatile private var pendingBonesNorm: FloatArray? = null
    private val viewBuf = FloatArray(BONES.size * 4)

    private val _state = MutableStateFlow(PoseHeightState())
    val state: StateFlow<PoseHeightState> = _state.asStateFlow()

    /** Clears the smoothing buffer (e.g. new person / screen re-entered). */
    fun reset() = executor.execute { smoother.reset() }

    fun close() {
        closed = true
        detector.close()
        thread.shutdown()
    }

    /** GL thread. [displayRotation] is Surface.ROTATION_* of the view's display. */
    fun onFrame(frame: Frame, session: Session, displayRotation: Int) {
        if (closed) return
        pendingBonesNorm?.let { norm -> pendingBonesNorm = null; mapBonesToView(frame, norm) }
        if (busy.get()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchMs < minIntervalMs) return
        lastLaunchMs = now

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            publish(HeightResult.fail(HeightReason.TRACKING_POOR), FloatArray(0))
            return
        }
        val img = try {
            frame.acquireCameraImage()
        } catch (e: Exception) {
            return // NotYetAvailable / ResourceExhausted / DeadlineExceeded: try next frame
        }
        val w = img.width
        val h = img.height
        val bytes = try { YuvToNv21.convert(img, nv21).also { nv21 = it } } finally { img.close() }

        val pose = camera.pose.toPose()
        val k = camera.imageIntrinsics.let {
            val f = it.focalLength; val c = it.principalPoint; val d = it.imageDimensions
            CameraIntrinsics(f[0], f[1], c[0], c[1], d[0], d[1])
        }.let { if (it.width != w || it.height != h) it.scaledTo(w, h) else it }
        val depth = depthSource.get(frame)?.let { DepthMapLookup(it.map, it.intrinsics, w, h, pose) }
        val planes = session.getAllTrackables(ArPlane::class.java)
            .filter { it.type == ArPlane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .map { p -> p.centerPose.toPose().let { Plane(it.translation, it.yAxis) } }
        val rotation = ImageRotation.backCameraRotation(sensorOrientation(session), displayRotation * 90)

        busy.set(true)
        val input = InputImage.fromByteArray(bytes, w, h, rotation, InputImage.IMAGE_FORMAT_NV21)
        detector.process(input)
            .addOnSuccessListener(executor) { result ->
                if (closed) return@addOnSuccessListener
                val landmarks = toLandmarks(result, w, h, rotation)
                val est = estimator.estimate(HeightFrame(landmarks, k, pose, depth, planes, TrackingStatus.TRACKING))
                publish(est, bonesNormalized(landmarks))
            }
            .addOnCompleteListener(executor) { busy.set(false) }
    }

    private fun publish(result: HeightResult, bonesNorm: FloatArray) = executor.execute {
        if (closed) return@execute
        val smoothed = smoother.add(result)
        _state.update { it.copy(smoothed = smoothed, latest = result) }
        if (bonesNorm.isEmpty()) _state.update { it.copy(skeletonView = FloatArray(0)) } else pendingBonesNorm = bonesNorm
    }

    private fun mapBonesToView(frame: Frame, norm: FloatArray) {
        val out = if (viewBuf.size == norm.size) viewBuf else FloatArray(norm.size)
        frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, norm, Coordinates2d.VIEW, out)
        val copy = out.copyOf()
        _state.update { it.copy(skeletonView = copy) }
    }

    private fun sensorOrientation(session: Session): Int = sensorOrientation ?: try {
        val cm = context.getSystemService(CameraManager::class.java)
        cm.getCameraCharacteristics(session.cameraConfig.cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    } catch (e: Exception) {
        90
    }.also { sensorOrientation = it }

    companion object {
        private val ML_KIT_TYPES = mapOf(
            BodyLandmark.NOSE to PoseLandmark.NOSE,
            BodyLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE, BodyLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE,
            BodyLandmark.LEFT_EAR to PoseLandmark.LEFT_EAR, BodyLandmark.RIGHT_EAR to PoseLandmark.RIGHT_EAR,
            BodyLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER, BodyLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            BodyLandmark.LEFT_HIP to PoseLandmark.LEFT_HIP, BodyLandmark.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
            BodyLandmark.LEFT_KNEE to PoseLandmark.LEFT_KNEE, BodyLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
            BodyLandmark.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE, BodyLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
            BodyLandmark.LEFT_HEEL to PoseLandmark.LEFT_HEEL, BodyLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_HEEL,
            BodyLandmark.LEFT_FOOT_INDEX to PoseLandmark.LEFT_FOOT_INDEX,
            BodyLandmark.RIGHT_FOOT_INDEX to PoseLandmark.RIGHT_FOOT_INDEX,
        )

        private val BONES = listOf(
            BodyLandmark.LEFT_EYE to BodyLandmark.RIGHT_EYE,
            BodyLandmark.LEFT_SHOULDER to BodyLandmark.RIGHT_SHOULDER, BodyLandmark.LEFT_HIP to BodyLandmark.RIGHT_HIP,
            BodyLandmark.LEFT_SHOULDER to BodyLandmark.LEFT_HIP, BodyLandmark.RIGHT_SHOULDER to BodyLandmark.RIGHT_HIP,
            BodyLandmark.LEFT_HIP to BodyLandmark.LEFT_KNEE, BodyLandmark.RIGHT_HIP to BodyLandmark.RIGHT_KNEE,
            BodyLandmark.LEFT_KNEE to BodyLandmark.LEFT_ANKLE, BodyLandmark.RIGHT_KNEE to BodyLandmark.RIGHT_ANKLE,
            BodyLandmark.LEFT_ANKLE to BodyLandmark.LEFT_HEEL, BodyLandmark.RIGHT_ANKLE to BodyLandmark.RIGHT_HEEL,
            BodyLandmark.LEFT_HEEL to BodyLandmark.LEFT_FOOT_INDEX, BodyLandmark.RIGHT_HEEL to BodyLandmark.RIGHT_FOOT_INDEX,
        )

        /** ML Kit pose (upright-image pixels) -> sensor-image pixels, matching ARCore's CPU-image intrinsics. */
        internal fun toLandmarks(pose: Pose, w: Int, h: Int, rotation: Int): PoseLandmarks {
            val pts = ML_KIT_TYPES.mapNotNull { (body, type) ->
                pose.getPoseLandmark(type)?.let { l ->
                    val (x, y) = ImageRotation.uprightToSensor(l.position.x, l.position.y, w, h, rotation)
                    body to Landmark2D(x, y, l.inFrameLikelihood)
                }
            }.toMap()
            return PoseLandmarks(pts, w, h)
        }

        /** Confident bones as normalised sensor-image coords (x0,y0,x1,y1,...), for drawing. */
        private fun bonesNormalized(lm: PoseLandmarks): FloatArray {
            val out = ArrayList<Float>()
            for ((a, b) in BONES) {
                val pa = lm[a]?.takeIf { it.inFrameLikelihood >= 0.5f } ?: continue
                val pb = lm[b]?.takeIf { it.inFrameLikelihood >= 0.5f } ?: continue
                out += pa.x / lm.imageWidth; out += pa.y / lm.imageHeight
                out += pb.x / lm.imageWidth; out += pb.y / lm.imageHeight
            }
            return out.toFloatArray()
        }
    }
}
