package app.openmeasure.measure.projection

import app.openmeasure.measure.geometry.Vec3

/** Point in view pixels, origin top-left, y down. */
data class ScreenPoint(val x: Float, val y: Float)

/** A segment clipped to the camera's near side and projected to view pixels. */
data class ScreenSegment(val a: ScreenPoint, val b: ScreenPoint) {
    val midpoint: ScreenPoint get() = ScreenPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

/**
 * World -> screen projection using ARCore's column-major (OpenGL) 4x4 view and projection matrices.
 * Pure maths so it can be unit-tested on the JVM.
 */
object ScreenProjection {
    private const val W_EPS = 1e-4f

    /** Projects [p]; null when it is behind (or on) the camera plane or not finite. */
    fun project(p: Vec3, view: FloatArray, proj: FloatArray, width: Float, height: Float): ScreenPoint? {
        val c = clip(p, view, proj)
        if (!(c[3] > W_EPS)) return null
        return toScreen(c, width, height)
    }

    /**
     * Projects the segment a-b, first clipping it to the half-space in front of the camera, so a line
     * with one end behind the user still draws up to the near side. Null if entirely behind.
     */
    fun projectSegment(a: Vec3, b: Vec3, view: FloatArray, proj: FloatArray, width: Float, height: Float): ScreenSegment? {
        var ca = clip(a, view, proj)
        var cb = clip(b, view, proj)
        val aIn = ca[3] > W_EPS
        val bIn = cb[3] > W_EPS
        if (!aIn && !bIn) return null
        if (!aIn || !bIn) {
            val t = (W_EPS - ca[3]) / (cb[3] - ca[3])
            val cut = FloatArray(4) { ca[it] + (cb[it] - ca[it]) * t }
            cut[3] = W_EPS
            if (!aIn) ca = cut else cb = cut
        }
        return ScreenSegment(toScreen(ca, width, height), toScreen(cb, width, height))
    }

    private fun toScreen(c: FloatArray, width: Float, height: Float) = ScreenPoint(
        (c[0] / c[3] * 0.5f + 0.5f) * width,
        (1f - (c[1] / c[3] * 0.5f + 0.5f)) * height,
    )

    /** clip = proj * view * [p, 1]. */
    private fun clip(p: Vec3, view: FloatArray, proj: FloatArray): FloatArray {
        val v = FloatArray(4)
        for (r in 0 until 4) v[r] = view[r] * p.x + view[4 + r] * p.y + view[8 + r] * p.z + view[12 + r]
        val out = FloatArray(4)
        for (r in 0 until 4) out[r] = proj[r] * v[0] + proj[4 + r] * v[1] + proj[8 + r] * v[2] + proj[12 + r] * v[3]
        return out
    }
}

/** Where to put a measurement label on screen. */
object LabelPlacement {
    /**
     * Midpoint of [seg], lifted [liftPx] above the line (smaller y) so it does not cover it, then clamped
     * so a label of half-size ([halfW], [halfH]) stays inside the view. Always screen-aligned (upright).
     */
    fun place(
        seg: ScreenSegment,
        width: Float,
        height: Float,
        halfW: Float,
        halfH: Float,
        liftPx: Float,
    ): ScreenPoint {
        val m = seg.midpoint
        val x = m.x.coerceIn(minOf(halfW, width / 2f), maxOf(width - halfW, width / 2f))
        val y = (m.y - liftPx).coerceIn(minOf(halfH, height / 2f), maxOf(height - halfH, height / 2f))
        return ScreenPoint(x, y)
    }
}
