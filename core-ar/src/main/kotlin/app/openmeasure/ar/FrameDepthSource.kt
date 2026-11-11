package app.openmeasure.ar

import android.media.Image
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthMap
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteOrder

/** One frame's depth map plus intrinsics already scaled to it. */
class FrameDepth(val map: DepthMap, val intrinsics: CameraIntrinsics)

/**
 * Copies ARCore depth (+ raw confidence) into a [DepthMap] once per frame and caches it by frame timestamp,
 * so the reticle hit, drag hit and snapping share a single acquisition. GL thread only.
 */
class FrameDepthSource {
    private var cachedTimestamp = Long.MIN_VALUE
    private var cached: FrameDepth? = null
    private val viewPt = FloatArray(2)
    private val normPt = FloatArray(2)

    /** Depth for [frame], or null when depth is unavailable (not yet / disabled). */
    fun get(frame: Frame): FrameDepth? {
        if (frame.timestamp == cachedTimestamp) return cached
        cachedTimestamp = frame.timestamp
        cached = acquire(frame)
        return cached
    }

    /** View pixel -> depth-image pixel-index coordinates. */
    fun viewToDepthPixel(frame: Frame, depth: FrameDepth, x: Float, y: Float): Pair<Float, Float> {
        viewPt[0] = x; viewPt[1] = y
        frame.transformCoordinates2d(Coordinates2d.VIEW, viewPt, Coordinates2d.IMAGE_NORMALIZED, normPt)
        return depth.intrinsics.normalizedToPixel(normPt[0], normPt[1])
    }

    private fun acquire(frame: Frame): FrameDepth? = try {
        frame.acquireDepthImage16Bits().use { depthImage ->
            val confidence = rawConfidence(frame, depthImage.width, depthImage.height)
            val map = DepthMap(depthImage.width, depthImage.height, readDepth(depthImage), confidence)
            val k = frame.camera.imageIntrinsics.let {
                val f = it.focalLength
                val c = it.principalPoint
                val d = it.imageDimensions
                CameraIntrinsics(f[0], f[1], c[0], c[1], d[0], d[1])
            }.scaledTo(map.width, map.height)
            FrameDepth(map, k)
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
