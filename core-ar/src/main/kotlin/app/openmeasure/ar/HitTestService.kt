package app.openmeasure.ar

import android.media.Image
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Quaternion
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap
import app.openmeasure.measure.hit.HitResolver
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.PlaneHit
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteOrder
import com.google.ar.core.HitResult as ArCoreHitResult
import com.google.ar.core.Pose as ArPose

/** Engine result plus the ARCore plane hit it came from (if any), for trackable-attached anchors. */
class ArHit(val result: HitResult, private val planeHit: ArCoreHitResult?) {
    /** Plane hits anchor to the plane (follows plane refinement); depth hits use a free anchor. */
    fun createAnchor(session: Session): Anchor? = when {
        !result.isHit -> null
        planeHit != null && result.source == app.openmeasure.measure.hit.HitSource.PLANE ->
            planeHit.trackable.createAnchor(result.pose!!.toArPose())
        else -> session.createAnchor(result.pose!!.toArPose())
    }
}

/**
 * Thin ARCore adapter: maps a [Frame] to core-measure inputs and lets [HitResolver] decide.
 * Must be called on the GL thread with the current frame (e.g. from ArCameraView.onFrame).
 */
class HitTestService(
    private val depthTester: DepthHitTester = DepthHitTester(),
    private val resolver: HitResolver = HitResolver(),
) {
    private val viewPt = FloatArray(2)
    private val normPt = FloatArray(2)

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

    private fun depthHit(frame: Frame, x: Float, y: Float, camPose: Pose) = try {
        frame.acquireDepthImage16Bits().use { depthImage ->
            val confidence = rawConfidence(frame, depthImage.width, depthImage.height)
            val map = DepthMap(depthImage.width, depthImage.height, readDepth(depthImage), confidence)
            val k = frame.camera.imageIntrinsics.let {
                val f = it.focalLength
                val c = it.principalPoint
                val d = it.imageDimensions
                CameraIntrinsics(f[0], f[1], c[0], c[1], d[0], d[1])
            }.scaledTo(map.width, map.height)
            viewPt[0] = x; viewPt[1] = y
            frame.transformCoordinates2d(Coordinates2d.VIEW, viewPt, Coordinates2d.IMAGE_NORMALIZED, normPt)
            val (u, v) = k.normalizedToPixel(normPt[0], normPt[1])
            depthTester.hitTest(map, k, u, v, camPose)
        }
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: IllegalStateException) {
        null // depth disabled on this device/config
    }

    private fun rawConfidence(frame: Frame, w: Int, h: Int): ByteArray? = try {
        frame.acquireRawDepthConfidenceImage().use { img ->
            if (img.width != w || img.height != h) null else readConfidence(img)
        }
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: IllegalStateException) {
        null
    }

    /** First tracked plane hit whose pose lies inside the plane polygon. */
    private fun planeHit(frame: Frame, x: Float, y: Float): ArCoreHitResult? =
        frame.hitTest(x, y).firstOrNull { hit ->
            val plane = hit.trackable as? Plane ?: return@firstOrNull false
            plane.trackingState == TrackingState.TRACKING &&
                plane.subsumedBy == null &&
                plane.isPoseInPolygon(hit.hitPose)
        }

    private fun readDepth(img: Image): ShortArray {
        val plane = img.planes[0]
        val buf = plane.buffer.order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray(img.width * img.height)
        for (row in 0 until img.height) {
            val base = row * plane.rowStride
            for (col in 0 until img.width) {
                out[row * img.width + col] = buf.getShort(base + col * plane.pixelStride)
            }
        }
        return out
    }

    private fun readConfidence(img: Image): ByteArray {
        val plane = img.planes[0]
        val buf = plane.buffer
        val out = ByteArray(img.width * img.height)
        for (row in 0 until img.height) {
            val base = row * plane.rowStride
            for (col in 0 until img.width) out[row * img.width + col] = buf.get(base + col * plane.pixelStride)
        }
        return out
    }
}

fun ArPose.toPose(): Pose = Pose(Vec3(tx(), ty(), tz()), Quaternion(qx(), qy(), qz(), qw()))

fun Pose.toArPose(): ArPose = ArPose(
    floatArrayOf(translation.x, translation.y, translation.z),
    floatArrayOf(rotation.x, rotation.y, rotation.z, rotation.w),
)
