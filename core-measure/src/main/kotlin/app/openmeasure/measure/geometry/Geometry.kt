package app.openmeasure.measure.geometry

import kotlin.math.abs
import kotlin.math.sqrt

/** 3D vector in metres (world or camera space). Immutable. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length(): Float = sqrt(this dot this)
    fun distanceTo(o: Vec3): Float = (this - o).length()
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-9f) ZERO else this * (1f / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/** Unit rotation quaternion (x, y, z, w), same component order as ARCore. */
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    /** Rotates [v]: v' = v + 2w(q×v) + 2q×(q×v). */
    fun rotate(v: Vec3): Vec3 {
        val q = Vec3(x, y, z)
        val t = (q cross v) * 2f
        return v + t * w + (q cross t)
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)
    }
}

/** Rigid transform local -> world, matching ARCore Pose semantics (rotate, then translate). */
data class Pose(val translation: Vec3, val rotation: Quaternion = Quaternion.IDENTITY) {
    fun transformPoint(local: Vec3): Vec3 = rotation.rotate(local) + translation
    fun rotateVector(local: Vec3): Vec3 = rotation.rotate(local)

    /** Local +Y axis in world space (ARCore plane hit poses use +Y as the surface normal). */
    val yAxis: Vec3 get() = rotation.rotate(Vec3(0f, 1f, 0f))

    companion object {
        val IDENTITY = Pose(Vec3.ZERO)
    }
}

/** Half-line origin + t * direction, t >= 0. [direction] need not be normalised. */
data class Ray(val origin: Vec3, val direction: Vec3)

/** Infinite plane through [point] with unit [normal]. */
data class Plane(val point: Vec3, val normal: Vec3) {
    /** Signed distance of [p] from the plane along the normal. */
    fun signedDistance(p: Vec3): Float = (p - point) dot normal

    /** Ray-plane intersection, or null if parallel (|cos| < eps) or behind the ray origin. */
    fun intersect(ray: Ray, eps: Float = 1e-6f): Vec3? {
        val denom = ray.direction dot normal
        if (abs(denom) < eps) return null
        val t = ((point - ray.origin) dot normal) / denom
        if (t < 0f) return null
        return ray.origin + ray.direction * t
    }
}
