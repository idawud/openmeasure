package app.openmeasure.ar

import app.openmeasure.measure.rect.DetectedRect
import app.openmeasure.measure.rect.RectDetector
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState

/**
 * Thin ARCore adapter for rectangle auto-detection (F9): Frame -> DepthMap -> [RectDetector].
 * GL thread only. Share the [FrameDepthSource] with [HitTestService] so depth is copied once per frame.
 */
class RectDetectService(
    private val depthSource: FrameDepthSource,
    private val detector: RectDetector = RectDetector(),
) {
    /** Rectangle around view pixel ([x], [y]) or null (no depth, not tracking, nothing rectangular): use manual corners. */
    fun detect(frame: Frame, x: Float, y: Float): DetectedRect? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val fd = depthSource.get(frame) ?: return null
        val (u, v) = depthSource.viewToDepthPixel(frame, fd, x, y)
        return detector.detect(fd.map, fd.intrinsics, u, v, frame.camera.pose.toPose())
    }
}
