package app.openmeasure

import android.util.Log
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.model.PointId

/**
 * Debug-only placement log (`adb logcat -s OpenMeasureMeasure`). Constructed with [enabled] = debuggable build;
 * when false every call returns before formatting, so release builds neither log nor pay for it.
 */
internal class MeasureLog(private val enabled: Boolean) {
    /**
     * One line per placed point: id, hit source/confidence/distance, whether it came from the pre-tap buffer or
     * the live fallback, camera speed and position; plus the distance to the previous point in cm when there are >= 2.
     */
    fun placed(id: PointId, hit: HitResult, pos: Vec3, fromBuffer: Boolean, speedMps: Float, points: List<Point>) {
        if (!enabled) return
        val prevCm = if (points.size >= 2) " prevCm=%.1f".format(points[points.size - 2].position.distanceTo(pos) * 100f) else ""
        val src = if (fromBuffer) "buffer" else "live"
        val line = "placed $id src=${hit.source} conf=%.2f depthM=%.3f from=$src speed=%.3fm/s pos=(%.4f, %.4f, %.4f)%s"
        Log.d(TAG, line.format(hit.confidence, hit.distanceM, speedMps, pos.x, pos.y, pos.z, prevCm))
    }

    private companion object {
        const val TAG = "OpenMeasureMeasure"
    }
}
