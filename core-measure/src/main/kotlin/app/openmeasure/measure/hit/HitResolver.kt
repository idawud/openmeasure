package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Ray
import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.max

enum class HitSource { DEPTH, PLANE, NONE }

/** Final hit-test answer. [position]/[pose] are null iff [source] == NONE. */
data class HitResult(
    val source: HitSource,
    val position: Vec3?,
    /** For PLANE: the ARCore hit pose (+Y = surface normal). For DEPTH: translation only. */
    val pose: Pose?,
    /** Surface normal when known (planes), else null. */
    val normal: Vec3?,
    /** 0..1. Callers combine with tracking state for the High/Medium/Low badge. */
    val confidence: Float,
    /** Straight-line distance from the camera in metres, 0 for NONE. */
    val distanceM: Float,
) {
    val isHit: Boolean get() = source != HitSource.NONE

    companion object {
        val NONE = HitResult(HitSource.NONE, null, null, null, 0f, 0f)
    }
}

/** A plane hit already filtered by the AR layer (tracking plane, pose inside polygon). */
data class PlaneHit(val pose: Pose) {
    val position: Vec3 get() = pose.translation
    val normal: Vec3 get() = pose.yAxis.normalized()
}

/**
 * Chooses between a depth-map hit and a plane hit.
 *
 * - Not tracking -> NONE, always.
 * - Both present and the depth point lies on the plane (within tolerance): snap to the plane
 *   (ray-plane intersection). Plane fits average many feature points, so they are less noisy than
 *   per-pixel depth; this is what keeps 1 m lines on floors/tables within ~1 cm.
 * - Depth disagrees with the plane and is confident: something sits on/in front of the plane -> DEPTH.
 * - Otherwise the plane, then a low-confidence depth hit, then NONE.
 */
class HitResolver(
    val minDepthConfidence: Float = 0.35f,
    val minUsableConfidence: Float = 0.15f,
    val agreementAbsM: Float = 0.02f,
    val agreementRel: Float = 0.02f,
    val planeBaseConfidence: Float = 0.85f,
    val fullConfidenceRangeM: Float = 2.5f,
    val zeroConfidenceRangeM: Float = 6.0f,
) {
    fun resolve(isTracking: Boolean, cameraPosition: Vec3, depth: DepthHit?, plane: PlaneHit?): HitResult {
        if (!isTracking) return HitResult.NONE
        if (depth != null && plane != null) {
            val planeGeom = Plane(plane.position, plane.normal)
            val dist = cameraPosition.distanceTo(depth.position)
            val tol = max(agreementAbsM, agreementRel * dist)
            if (abs(planeGeom.signedDistance(depth.position)) <= tol) {
                val ray = Ray(cameraPosition, depth.position - cameraPosition)
                val snapped = planeGeom.intersect(ray) ?: plane.position
                val conf = max(depth.confidence, planeConfidence(cameraPosition.distanceTo(snapped)))
                return planeResult(plane, snapped, conf, cameraPosition)
            }
            if (depth.confidence >= minDepthConfidence) return depthResult(depth, cameraPosition)
            return planeResult(plane, plane.position, planeConfidence(cameraPosition.distanceTo(plane.position)), cameraPosition)
        }
        if (plane != null) {
            return planeResult(plane, plane.position, planeConfidence(cameraPosition.distanceTo(plane.position)), cameraPosition)
        }
        if (depth != null && depth.confidence >= minUsableConfidence) return depthResult(depth, cameraPosition)
        return HitResult.NONE
    }

    fun planeConfidence(distanceM: Float): Float {
        val f = 1f - (distanceM - fullConfidenceRangeM) / (zeroConfidenceRangeM - fullConfidenceRangeM)
        return planeBaseConfidence * f.coerceIn(0f, 1f)
    }

    private fun planeResult(plane: PlaneHit, pos: Vec3, conf: Float, cam: Vec3) =
        HitResult(HitSource.PLANE, pos, Pose(pos, plane.pose.rotation), plane.normal, conf, cam.distanceTo(pos))

    private fun depthResult(d: DepthHit, cam: Vec3) =
        HitResult(HitSource.DEPTH, d.position, Pose(d.position), null, d.confidence, cam.distanceTo(d.position))
}
