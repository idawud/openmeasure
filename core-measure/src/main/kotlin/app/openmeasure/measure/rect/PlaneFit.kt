package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** 2D point in a [PlaneBasis] (metres). Double precision: area and angle maths happen here. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    infix fun dot(o: Vec2): Double = x * o.x + y * o.y
    /** z component of the 3D cross product. */
    infix fun cross(o: Vec2): Double = x * o.y - y * o.x
    fun length(): Double = sqrt(this dot this)
}

/** Right-handed orthonormal frame on a plane: [u] x [v] = [normal]. */
data class PlaneBasis(val origin: Vec3, val u: Vec3, val v: Vec3, val normal: Vec3) {
    fun to2d(p: Vec3): Vec2 {
        val d = p - origin
        return Vec2((d dot u).toDouble(), (d dot v).toDouble())
    }

    fun to3d(q: Vec2): Vec3 = origin + u * q.x.toFloat() + v * q.y.toFloat()

    /** Orthogonal projection of [p] onto the plane. */
    fun project(p: Vec3): Vec3 = p - normal * ((p - origin) dot normal)

    fun distance(p: Vec3): Float = (p - origin) dot normal

    /** Same plane, opposite side: v and normal flipped (still right-handed). */
    fun flipped(): PlaneBasis = PlaneBasis(origin, u, -v, -normal)

    val plane: Plane get() = Plane(origin, normal)

    companion object {
        /** Basis with unit [normal] whose [u] axis is [hint] projected into the plane (any perpendicular if parallel). */
        fun of(origin: Vec3, normal: Vec3, hint: Vec3): PlaneBasis {
            val n = normal.normalized()
            var u = hint - n * (hint dot n)
            if (u.length() < 1e-6f) {
                val alt = if (abs(n.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
                u = alt - n * (alt dot n)
            }
            u = u.normalized()
            return PlaneBasis(origin, u, n cross u, n)
        }
    }
}

/**
 * Running least-squares (total least squares: perpendicular distance) plane fit. Sums are kept in double
 * relative to the first point added, so world coordinates far from the origin keep sub-millimetre precision.
 */
class PlaneAccumulator {
    private var ox = 0.0
    private var oy = 0.0
    private var oz = 0.0
    var count = 0
        private set
    private val s = DoubleArray(9) // sx sy sz sxx sxy sxz syy syz szz

    fun add(x: Float, y: Float, z: Float) {
        if (count == 0) { ox = x.toDouble(); oy = y.toDouble(); oz = z.toDouble() }
        val a = x - ox
        val b = y - oy
        val c = z - oz
        s[0] += a; s[1] += b; s[2] += c
        s[3] += a * a; s[4] += a * b; s[5] += a * c
        s[6] += b * b; s[7] += b * c; s[8] += c * c
        count++
    }

    fun add(p: Vec3) = add(p.x, p.y, p.z)

    /**
     * Best plane (centroid + normal of least variance), or null with < 3 points or when the points are
     * (near-)collinear: middle eigenvalue below [minSpreadRatio] x largest.
     */
    fun fit(minSpreadRatio: Double = 1e-6): Plane? {
        if (count < 3) return null
        val n = count.toDouble()
        val mx = s[0] / n
        val my = s[1] / n
        val mz = s[2] / n
        val cov = doubleArrayOf(
            s[3] / n - mx * mx, s[4] / n - mx * my, s[5] / n - mx * mz,
            s[4] / n - mx * my, s[6] / n - my * my, s[7] / n - my * mz,
            s[5] / n - mx * mz, s[7] / n - my * mz, s[8] / n - mz * mz,
        )
        val (values, vectors) = SymEigen3.decompose(cov)
        if (values[2] <= 0.0 || values[1] < minSpreadRatio * values[2]) return null
        val normal = Vec3(vectors[0].toFloat(), vectors[1].toFloat(), vectors[2].toFloat()).normalized()
        val centroid = Vec3((ox + mx).toFloat(), (oy + my).toFloat(), (oz + mz).toFloat())
        return Plane(centroid, normal)
    }
}

/** Jacobi eigen-decomposition of a symmetric 3x3 matrix (row-major). */
internal object SymEigen3 {
    /**
     * Returns eigenvalues sorted ascending and the eigenvector of the SMALLEST eigenvalue (x, y, z).
     */
    fun decompose(m: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val a = m.copyOf()
        val v = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        repeat(50) {
            val off = a[1] * a[1] + a[2] * a[2] + a[5] * a[5]
            if (off < 1e-30) return@repeat
            rotate(a, v, 0, 1); rotate(a, v, 0, 2); rotate(a, v, 1, 2)
        }
        val ev = doubleArrayOf(a[0], a[4], a[8])
        val idx = (0..2).sortedBy { ev[it] }
        val k = idx[0]
        return Pair(doubleArrayOf(ev[idx[0]], ev[idx[1]], ev[idx[2]]), doubleArrayOf(v[k], v[3 + k], v[6 + k]))
    }

    private fun rotate(a: DoubleArray, v: DoubleArray, p: Int, q: Int) {
        val apq = a[p * 3 + q]
        if (abs(apq) < 1e-300) return
        val app = a[p * 3 + p]
        val aqq = a[q * 3 + q]
        val theta = (aqq - app) / (2 * apq)
        val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
        val c = 1 / sqrt(t * t + 1)
        val s = t * c
        for (k in 0..2) { // A = A * J
            val akp = a[k * 3 + p]
            val akq = a[k * 3 + q]
            a[k * 3 + p] = c * akp - s * akq
            a[k * 3 + q] = s * akp + c * akq
        }
        for (k in 0..2) { // A = J^T * A
            val apk = a[p * 3 + k]
            val aqk = a[q * 3 + k]
            a[p * 3 + k] = c * apk - s * aqk
            a[q * 3 + k] = s * apk + c * aqk
        }
        for (k in 0..2) { // V = V * J (columns are eigenvectors)
            val vkp = v[k * 3 + p]
            val vkq = v[k * 3 + q]
            v[k * 3 + p] = c * vkp - s * vkq
            v[k * 3 + q] = s * vkp + c * vkq
        }
    }
}
