package app.openmeasure.measure.height

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Quaternion
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.height.BodyLandmark.*
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic pinhole scene: a person modelled as a vertical billboard at world z = [personZ] standing on the floor y = 0,
 * a back wall 3 m behind, ARCore axes (+Y up, camera looks along its -Z).
 */
class SyntheticPerson(
    val heightM: Float,
    val personZ: Float,
    /** World x shift applied to head landmarks / shoulders to simulate leaning. */
    val headShiftX: Float = 0f,
    val shoulderShiftX: Float = 0f,
) {
    val world: Map<BodyLandmark, Vec3> = run {
        val h = heightM
        val z = personZ
        val eyeY = h - HeightEstimator.EYE_TO_VERTEX_M
        mapOf(
            NOSE to Vec3(headShiftX, h - HeightEstimator.NOSE_TO_VERTEX_M, z),
            LEFT_EYE to Vec3(headShiftX + 0.032f, eyeY, z), RIGHT_EYE to Vec3(headShiftX - 0.032f, eyeY, z),
            LEFT_EAR to Vec3(headShiftX + 0.075f, h - HeightEstimator.EAR_TO_VERTEX_M, z),
            RIGHT_EAR to Vec3(headShiftX - 0.075f, h - HeightEstimator.EAR_TO_VERTEX_M, z),
            LEFT_SHOULDER to Vec3(shoulderShiftX + 0.19f, 0.818f * h, z),
            RIGHT_SHOULDER to Vec3(shoulderShiftX - 0.19f, 0.818f * h, z),
            LEFT_HIP to Vec3(0.09f, 0.52f * h, z), RIGHT_HIP to Vec3(-0.09f, 0.52f * h, z),
            LEFT_KNEE to Vec3(0.09f, 0.285f * h, z), RIGHT_KNEE to Vec3(-0.09f, 0.285f * h, z),
            LEFT_ANKLE to Vec3(0.09f, 0.075f, z), RIGHT_ANKLE to Vec3(-0.09f, 0.075f, z),
            LEFT_HEEL to Vec3(0.09f, 0.03f, z - 0.03f), RIGHT_HEEL to Vec3(-0.09f, 0.03f, z - 0.03f),
            LEFT_FOOT_INDEX to Vec3(0.1f, 0.02f, z + 0.15f), RIGHT_FOOT_INDEX to Vec3(-0.1f, 0.02f, z + 0.15f),
        )
    }

    fun landmarks(pose: Pose, k: CameraIntrinsics, likelihood: Float = 0.95f): PoseLandmarks {
        val pts = world.mapValues { (_, p) ->
            val (u, v) = project(p, pose, k)
            Landmark2D(u, v, likelihood)
        }
        return PoseLandmarks(pts, k.width, k.height)
    }

    /** Rendered z-depth map (+ full confidence) for [pose] at the resolution of [kd]. */
    fun depthMap(pose: Pose, kd: CameraIntrinsics): DepthMap {
        val depth = ShortArray(kd.width * kd.height)
        for (j in 0 until kd.height) for (i in 0 until kd.width) {
            val dir = pose.rotateVector(DepthHitTester.unprojectToCamera(kd, i.toFloat(), j.toFloat(), 1f))
            val o = pose.translation
            var best = Float.MAX_VALUE
            fun consider(t: Float, ok: (Vec3) -> Boolean) {
                if (t > 0f && t < best && ok(o + dir * t)) best = t
            }
            if (dir.z != 0f) {
                consider((personZ - o.z) / dir.z) { p -> p.x in -0.6f..0.6f && p.y in 0f..heightM }
                consider((personZ - 3f - o.z) / dir.z) { true }
            }
            if (dir.y != 0f) consider((0f - o.y) / dir.y) { true }
            // dir has unit z-depth, so t is the z-depth in metres.
            if (best != Float.MAX_VALUE) depth[j * kd.width + i] = (best * 1000f).toInt().coerceAtMost(30000).toShort()
        }
        return DepthMap(kd.width, kd.height, depth, ByteArray(kd.width * kd.height) { 255.toByte() })
    }

    fun frame(pose: Pose, withDepth: Boolean = true, planes: List<Plane> = listOf(FLOOR), likelihood: Float = 0.95f): HeightFrame {
        val kd = K.scaledTo(160, 120)
        val lookup = if (withDepth) DepthMapLookup(depthMap(pose, kd), kd, K.width, K.height, pose) else null
        return HeightFrame(landmarks(pose, K, likelihood), K, pose, lookup, planes)
    }

    companion object {
        val K = CameraIntrinsics(500f, 500f, 319.5f, 239.5f, 640, 480)
        val FLOOR = Plane(Vec3(0.3f, 0f, -1f), Vec3(0f, 1f, 0f))

        fun project(p: Vec3, pose: Pose, k: CameraIntrinsics): Pair<Float, Float> {
            val q = pose.rotation
            val c = Quaternion(-q.x, -q.y, -q.z, q.w).rotate(p - pose.translation)
            return Pair(k.cx + k.fx * c.x / -c.z, k.cy - k.fy * c.y / -c.z)
        }

        fun axisAngle(axis: Vec3, deg: Float): Quaternion {
            val a = Math.toRadians(deg.toDouble()) / 2
            val n = axis.normalized() * sin(a).toFloat()
            return Quaternion(n.x, n.y, n.z, cos(a).toFloat())
        }

        /** Hamilton product: rotating by (a * b) applies b first, then a. */
        fun mul(a: Quaternion, b: Quaternion) = Quaternion(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        )

        /** Phone held in portrait (sensor long side vertical), pitched down by [pitchDownDeg]. */
        fun portraitPose(cameraY: Float, pitchDownDeg: Float = 0f): Pose {
            val roll = axisAngle(Vec3(0f, 0f, 1f), 90f)
            val pitch = axisAngle(Vec3(1f, 0f, 0f), -pitchDownDeg)
            return Pose(Vec3(0f, cameraY, 0f), mul(pitch, roll))
        }
    }
}
