package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Camera-space points of valid depth pixels (ARCore camera axes), computed on demand. */
internal class CameraPoints(private val depth: DepthMap, private val k: CameraIntrinsics, private val minMm: Int, private val maxMm: Int) {
    val w = depth.width
    val h = depth.height

    fun at(x: Int, y: Int): Vec3? {
        if (x !in 0 until w || y !in 0 until h) return null
        val d = depth.depthAt(x, y)
        if (d !in minMm..maxMm) return null
        return DepthHitTester.unprojectToCamera(k, x.toFloat(), y.toFloat(), d / 1000f)
    }
}

/** Pixel footprints on a camera-space plane: rays through pixel corners intersected with the plane. */
internal class Footprint(private val k: CameraIntrinsics, private val plane: Plane, private val basis: PlaneBasis) {
    private fun hit(u: Float, v: Float): Vec2? {
        val r = DepthHitTester.unprojectToCamera(k, u, v, 1f)
        val denom = r dot plane.normal
        if (abs(denom) < 1e-6f) return null
        val t = (plane.point dot plane.normal) / denom
        if (t <= 0f) return null
        return basis.to2d(r * t)
    }

    /** The 4 footprint corners of pixel (x, y) in plane coordinates (cyclic order), or null at grazing angles. */
    fun corners(x: Int, y: Int): List<Vec2>? {
        val a = hit(x - 0.5f, y - 0.5f) ?: return null
        val b = hit(x + 0.5f, y - 0.5f) ?: return null
        val c = hit(x + 0.5f, y + 0.5f) ?: return null
        val d = hit(x - 0.5f, y + 0.5f) ?: return null
        return listOf(a, b, c, d)
    }
}

/**
 * Area moments of a union of pixel footprints (each treated as a parallelogram). Including each footprint's own
 * second moment makes the result that of the union of footprints, so a rectangle's width is sqrt(12 var) without
 * the -1 px bias of point samples, and edge raggedness averages out along the whole edge.
 */
internal class RegionMoments {
    var area = 0.0
        private set
    var count = 0
        private set
    private var sx = 0.0
    private var sy = 0.0
    private var sxx = 0.0
    private var sxy = 0.0
    private var syy = 0.0

    fun add(c: List<Vec2>) {
        val a = abs((c[2] - c[0]) cross (c[3] - c[1])) / 2
        val mx = (c[0].x + c[1].x + c[2].x + c[3].x) / 4
        val my = (c[0].y + c[1].y + c[2].y + c[3].y) / 4
        val e1 = ((c[1] - c[0]) + (c[2] - c[3])) * 0.5
        val e2 = ((c[3] - c[0]) + (c[2] - c[1])) * 0.5
        area += a
        count++
        sx += a * mx
        sy += a * my
        sxx += a * (mx * mx + (e1.x * e1.x + e2.x * e2.x) / 12)
        sxy += a * (mx * my + (e1.x * e1.y + e2.x * e2.y) / 12)
        syy += a * (my * my + (e1.y * e1.y + e2.y * e2.y) / 12)
    }

    val centroid: Vec2 get() = Vec2(sx / area, sy / area)

    /** Central covariance (xx, xy, yy). */
    fun covariance(): Triple<Double, Double, Double> {
        val c = centroid
        return Triple(sxx / area - c.x * c.x, sxy / area - c.x * c.y, syy / area - c.y * c.y)
    }

    /** Principal axis angle (radians) and anisotropy (l1 - l2) / (l1 + l2) in 0..1. */
    fun principalAxis(): Pair<Double, Double> {
        val (a, b, c) = covariance()
        val angle = 0.5 * atan2(2 * b, a - c)
        val root = sqrt(((a - c) / 2) * ((a - c) / 2) + b * b)
        val sum = a + c
        return Pair(angle, if (sum > 0) 2 * root / sum else 0.0)
    }

    /** Equivalent-rectangle extent along unit direction [d]: sqrt(12 * variance along d). */
    fun extentAlong(d: Vec2): Double {
        val (a, b, c) = covariance()
        val v = d.x * d.x * a + 2 * d.x * d.y * b + d.y * d.y * c
        return sqrt(12 * v.coerceAtLeast(0.0))
    }

    companion object {
        fun unit(angle: Double) = Vec2(cos(angle), sin(angle))
    }
}

internal class IntArrayStack {
    private var data = IntArray(256)
    private var size = 0
    fun push(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    fun pop(): Int = data[--size]
    fun isNotEmpty() = size > 0
}
