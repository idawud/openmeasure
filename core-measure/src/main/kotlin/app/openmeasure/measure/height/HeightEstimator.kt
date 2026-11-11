package app.openmeasure.measure.height

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Ray
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.height.BodyLandmark.*
import app.openmeasure.measure.hit.DepthHit
import app.openmeasure.measure.hit.DepthHitTester
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max

/**
 * Standing height from pose landmarks + depth (F10).
 *
 * Method: the eye (else ear, else nose) pixel is lifted to 3D at the person's depth (median of face patches,
 * falling back to the chest/hip depth); the floor is the ARCore horizontal plane hit by the foot rays (else depth at the
 * feet minus a landmark-above-floor offset). Height = distance of the eye point above the floor along the plane normal
 * (gravity up) + an anthropometric eye-to-crown offset, since no pose model has a head-top landmark.
 *
 * Error budget at 2-3 m (1 sigma): crown offset ~1 cm (adult spread; small children are over-estimated by ~1-2 cm),
 * eye landmark ~1 cm (2-3 px), plane floor ~0.5-1 cm (depth floor ~2 cm), depth error mostly along the view ray so
 * only ~sin(elevation) of it reaches the vertical. Expect about +-2 cm per frame, +-1.5 cm after smoothing.
 * Height includes shoes and excludes hair volume.
 */
class HeightEstimator(
    val minLikelihood: Float = 0.5f,
    /** Required landmarks must be at least this fraction of the image size inside the edges. */
    val frameMarginFraction: Float = 0.02f,
    val minDistanceM: Float = 1.5f,
    val maxDistanceM: Float = 5f,
    /** Max torso and leg angle from gravity-up. */
    val maxLeanDeg: Float = 20f,
) {
    fun estimate(f: HeightFrame): HeightResult {
        if (f.tracking != TrackingStatus.TRACKING) return HeightResult.fail(HeightReason.TRACKING_POOR)
        val lm = f.landmarks
        if (lm.points.values.count { it.inFrameLikelihood >= minLikelihood } < MIN_PERSON_LANDMARKS) {
            return HeightResult.fail(HeightReason.NO_PERSON)
        }
        val head = headReference(lm) ?: return HeightResult.fail(HeightReason.PARTIAL_BODY)
        val ankles = ANKLES.map { visible(lm, it) ?: return HeightResult.fail(HeightReason.PARTIAL_BODY) }
        val feet = (FEET + ANKLES).mapNotNull { l -> visible(lm, l)?.let { l to it } }
        if (feet.none { it.first in FEET }) return HeightResult.fail(HeightReason.PARTIAL_BODY)
        val torso = TORSO.associateWith { l ->
            lm[l]?.takeIf { it.inFrameLikelihood >= minLikelihood } ?: return HeightResult.fail(HeightReason.PARTIAL_BODY)
        }
        val depth = f.depth ?: return HeightResult.fail(HeightReason.LOW_DEPTH_CONFIDENCE)

        // Person depth: face patches unless they disagree with the torso (hair/background bleed).
        val up = f.up.normalized()
        val cam = f.cameraPose.translation
        fun range(p: Vec3) = horizontal(p - cam, up).length()
        val headHits = HEAD_ALL.mapNotNull { visible(lm, it) }.mapNotNull { depth.pointAt(it.x, it.y) }
        val torsoHits = listOf(mid(torso[LEFT_SHOULDER]!!, torso[RIGHT_SHOULDER]!!), mid(torso[LEFT_HIP]!!, torso[RIGHT_HIP]!!))
            .mapNotNull { depth.pointAt(it.first, it.second) }
        val headRange = medianOrNull(headHits.map { range(it.position) })
        val torsoRange = medianOrNull(torsoHits.map { range(it.position) })
        val useHead = headRange != null && (torsoRange == null || abs(headRange - torsoRange) <= MAX_HEAD_TORSO_RANGE_GAP_M)
        val used = if (useHead) headHits else torsoHits
        if (used.isEmpty()) return HeightResult.fail(HeightReason.LOW_DEPTH_CONFIDENCE)
        val depthConf = median(used.map { it.confidence })
        val headPoint = if (useHead) {
            worldAt(f, head.x, head.y, median(headHits.map { it.depthM }))
        } else {
            val anchor = torsoHits.minBy { abs(range(it.position) - torsoRange!!) }.position
            verticalPlane(anchor, cam, up).intersect(ray(f, head.x, head.y))
                ?: return HeightResult.fail(HeightReason.LOW_DEPTH_CONFIDENCE)
        }
        val dist = range(headPoint)
        if (dist < minDistanceM) return HeightResult.fail(HeightReason.TOO_CLOSE, dist)
        if (dist > maxDistanceM) return HeightResult.fail(HeightReason.TOO_FAR, dist)

        // Body points: own depth when it lies near the vertical body plane, else the ray-plane intersection.
        val bodyPlane = verticalPlane(headPoint, cam, up)
        fun body(l: Landmark2D): Vec3 {
            depth.pointAt(l.x, l.y)?.position
                ?.takeIf { abs(bodyPlane.signedDistance(it)) <= MAX_LANDMARK_PLANE_GAP_M }?.let { return it }
            return bodyPlane.intersect(ray(f, l.x, l.y)) ?: headPoint
        }
        // Upright: torso (hips->shoulders) and legs (ankles->hips) near vertical, legs clearly longer than torso.
        val shoulders = (body(torso[LEFT_SHOULDER]!!) + body(torso[RIGHT_SHOULDER]!!)) * 0.5f
        val hips = (body(torso[LEFT_HIP]!!) + body(torso[RIGHT_HIP]!!)) * 0.5f
        val ankleMid = (body(ankles[0]) + body(ankles[1])) * 0.5f
        val torsoVec = shoulders - hips
        val legVec = hips - ankleMid
        val lean = max(angleDeg(torsoVec, up), angleDeg(legVec, up))
        val torsoV = torsoVec dot up
        if (torsoV <= 0f || (legVec dot up) < MIN_LEG_TO_TORSO_RATIO * torsoV || lean > maxLeanDeg) {
            return HeightResult.fail(HeightReason.NOT_UPRIGHT, dist)
        }

        val floor = findFloor(f, up, feet, bodyPlane, headPoint, depth) ?: return HeightResult.fail(HeightReason.NO_FLOOR, dist)
        val height = floor.plane.signedDistance(headPoint) + head.vertexOffsetM
        if (height !in MIN_HEIGHT_M..MAX_HEIGHT_M) return HeightResult.fail(HeightReason.NO_PERSON, dist)

        val likelihood = (head.landmarks + ankles + feet.filter { it.first in FEET }.map { it.second }.maxBy { it.inFrameLikelihood })
            .minOf { it.inFrameLikelihood }
        val landmarkFactor = 0.5f + 0.5f * ((likelihood - minLikelihood) / (FULL_LIKELIHOOD - minLikelihood)).coerceIn(0f, 1f)
        val uprightFactor = 1f - 0.5f * (lean / maxLeanDeg).let { it * it }
        val floorFactor = if (floor.source == FloorSource.PLANE) PLANE_FLOOR_FACTOR else DEPTH_FLOOR_FACTOR
        val score = minOf(landmarkFactor, depthConf, floorFactor, uprightFactor).coerceIn(0f, 1f)
        var level = ConfidenceScorer.levelFor(score)
        if (level == ConfidenceLevel.HIGH && (floor.source == FloorSource.DEPTH || dist > HIGH_MAX_DISTANCE_M)) {
            level = ConfidenceLevel.MEDIUM
        }
        if (level == ConfidenceLevel.NONE) return HeightResult.fail(HeightReason.LOW_DEPTH_CONFIDENCE, dist)
        return HeightResult(height, level, HeightReason.OK, score, dist, floor.source)
    }

    private class Floor(val plane: Plane, val source: FloorSource)

    /**
     * Prefers an upward plane that the foot rays hit right under the person (and, when foot depth exists, at the
     * height the feet say); otherwise depth at the feet minus the landmark-above-floor offsets.
     */
    private fun findFloor(
        f: HeightFrame, up: Vec3, feet: List<Pair<BodyLandmark, Landmark2D>>, bodyPlane: Plane, head: Vec3, depth: DepthLookup,
    ): Floor? {
        val footFloorHeights = feet.mapNotNull { (type, l) ->
            val hit: DepthHit = depth.pointAt(l.x, l.y) ?: return@mapNotNull null
            if (abs(bodyPlane.signedDistance(hit.position)) > MAX_FOOT_PLANE_GAP_M) return@mapNotNull null
            (hit.position dot up) - aboveFloor(type)
        }
        val footRef = medianOrNull(footFloorHeights)
        val headH = head dot up
        val cosMax = cos(Math.toRadians(MAX_PLANE_TILT_DEG.toDouble())).toFloat()
        val planes = f.floorPlanes.mapNotNull { p ->
            val n = p.normal.normalized()
            if ((n dot up) < cosMax) return@mapNotNull null
            val planeH = p.point dot up
            if (headH - planeH < MIN_EYE_ABOVE_FLOOR_M) return@mapNotNull null
            if (footRef != null && abs(planeH - footRef) > MAX_PLANE_FOOT_GAP_M) return@mapNotNull null
            val plane = Plane(p.point, n)
            val hits = feet.mapNotNull { (_, l) -> plane.intersect(ray(f, l.x, l.y)) }
            if (hits.isEmpty()) return@mapNotNull null
            val floorPt = hits.reduce(Vec3::plus) * (1f / hits.size)
            if (horizontal(floorPt - head, up).length() > MAX_FLOOR_HEAD_OFFSET_M) return@mapNotNull null
            plane to planeH
        }
        val best = if (footRef != null) planes.minByOrNull { abs(it.second - footRef) } else planes.minByOrNull { it.second }
        if (best != null) return Floor(best.first, FloorSource.PLANE)
        return footRef?.let { Floor(Plane(up * it, up), FloorSource.DEPTH) }
    }

    private class HeadRef(val landmarks: List<Landmark2D>, val x: Float, val y: Float, val vertexOffsetM: Float)

    private fun headReference(lm: PoseLandmarks): HeadRef? {
        for ((set, offset) in HEAD_REFS) {
            val vis = set.mapNotNull { visible(lm, it) }
            if (vis.isNotEmpty()) return HeadRef(vis, vis.map { it.x }.average().toFloat(), vis.map { it.y }.average().toFloat(), offset)
        }
        return null
    }

    /** Landmark if confident and inside the frame margins. */
    private fun visible(lm: PoseLandmarks, type: BodyLandmark): Landmark2D? {
        val l = lm[type] ?: return null
        if (l.inFrameLikelihood < minLikelihood) return null
        val mx = frameMarginFraction * lm.imageWidth
        val my = frameMarginFraction * lm.imageHeight
        return l.takeIf { it.x in mx..(lm.imageWidth - mx) && it.y in my..(lm.imageHeight - my) }
    }

    companion object {
        /**
         * Eye (pupil) to crown, standing, adults. ANSUR II / NASA-STD-3000: stature minus eye height = 11-12.5 cm
         * (SD ~0.8 cm). Children: ~10 cm, so their height reads ~1-2 cm high.
         */
        const val EYE_TO_VERTEX_M = 0.115f
        /** Ear (tragion) to crown, adults ~12.5-13.5 cm. Used when no eye is visible. */
        const val EAR_TO_VERTEX_M = 0.13f
        /** Nose tip to crown (~4.5 cm below the eyes). Last resort. */
        const val NOSE_TO_VERTEX_M = 0.16f
        /** Heel landmark (back of heel) above the floor. */
        const val HEEL_ABOVE_FLOOR_M = 0.03f
        /** Toe tip above the floor. */
        const val FOOT_INDEX_ABOVE_FLOOR_M = 0.02f
        /** Ankle (lateral malleolus) above the floor, adults ~6.5-8 cm. */
        const val ANKLE_ABOVE_FLOOR_M = 0.075f

        const val MIN_PERSON_LANDMARKS = 6
        const val FULL_LIKELIHOOD = 0.9f
        const val MIN_LEG_TO_TORSO_RATIO = 1.2f
        /** Face vs torso horizontal range disagreement beyond which face depth is treated as background bleed. */
        const val MAX_HEAD_TORSO_RANGE_GAP_M = 0.5f
        /** Landmark depth farther than this from the vertical body plane is ignored (background / floor behind). */
        const val MAX_LANDMARK_PLANE_GAP_M = 0.4f
        const val MAX_FOOT_PLANE_GAP_M = 0.6f
        const val MAX_PLANE_TILT_DEG = 10f
        const val MAX_PLANE_FOOT_GAP_M = 0.15f
        const val MAX_FLOOR_HEAD_OFFSET_M = 1.0f
        const val MIN_EYE_ABOVE_FLOOR_M = 0.4f
        const val MIN_HEIGHT_M = 0.5f
        const val MAX_HEIGHT_M = 2.5f
        const val PLANE_FLOOR_FACTOR = 1.0f
        const val DEPTH_FLOOR_FACTOR = 0.7f
        /** Beyond this the badge is capped at MEDIUM (landmark pixels get coarse). */
        const val HIGH_MAX_DISTANCE_M = 3.5f

        private val EYES = listOf(LEFT_EYE, RIGHT_EYE)
        private val EARS = listOf(LEFT_EAR, RIGHT_EAR)
        private val HEAD_REFS = listOf(EYES to EYE_TO_VERTEX_M, EARS to EAR_TO_VERTEX_M, listOf(NOSE) to NOSE_TO_VERTEX_M)
        private val HEAD_ALL = EYES + EARS + NOSE
        private val ANKLES = listOf(LEFT_ANKLE, RIGHT_ANKLE)
        private val FEET = listOf(LEFT_HEEL, RIGHT_HEEL, LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX)
        private val TORSO = listOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)

        private fun aboveFloor(t: BodyLandmark): Float = when (t) {
            LEFT_HEEL, RIGHT_HEEL -> HEEL_ABOVE_FLOOR_M
            LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX -> FOOT_INDEX_ABOVE_FLOOR_M
            else -> ANKLE_ABOVE_FLOOR_M
        }

        private fun worldAt(f: HeightFrame, u: Float, v: Float, depthM: Float): Vec3 =
            f.cameraPose.transformPoint(DepthHitTester.unprojectToCamera(f.intrinsics, u, v, depthM))

        private fun ray(f: HeightFrame, u: Float, v: Float): Ray =
            Ray(f.cameraPose.translation, f.cameraPose.rotateVector(DepthHitTester.unprojectToCamera(f.intrinsics, u, v, 1f)))

        private fun horizontal(v: Vec3, up: Vec3): Vec3 = v - up * (v dot up)

        /** Vertical plane through [p] facing the camera (the standing person's body plane). */
        private fun verticalPlane(p: Vec3, cam: Vec3, up: Vec3): Plane = Plane(p, horizontal(cam - p, up).normalized())

        private fun angleDeg(v: Vec3, up: Vec3): Float {
            val l = v.length()
            if (l < 1e-6f) return 90f
            return Math.toDegrees(acos(((v dot up) / l).coerceIn(-1f, 1f).toDouble())).toFloat()
        }

        private fun mid(a: Landmark2D, b: Landmark2D) = Pair((a.x + b.x) / 2f, (a.y + b.y) / 2f)

        internal fun median(values: List<Float>): Float {
            val s = values.sorted()
            val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2f
        }

        private fun medianOrNull(values: List<Float>): Float? = if (values.isEmpty()) null else median(values)
    }
}
