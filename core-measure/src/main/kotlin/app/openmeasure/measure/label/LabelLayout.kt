package app.openmeasure.measure.label

import app.openmeasure.measure.projection.ScreenPoint

/** A label wanting to be centred at ([x], [y]) with size [w] x [h] (view pixels). */
data class LabelBox(val x: Float, val y: Float, val w: Float, val h: Float)

/** Pure screen-space label de-overlapping. */
object LabelLayout {
    /**
     * Returns one centre per input box (same order), nudged apart so boxes do not overlap (best effort,
     * [iterations] relaxation passes) and clamped inside a [width] x [height] view. Boxes that do not
     * overlap are never moved except for clamping. [gap] is the minimum clear space between boxes.
     */
    fun resolve(
        boxes: List<LabelBox>,
        width: Float,
        height: Float,
        gap: Float = 4f,
        iterations: Int = 24,
    ): List<ScreenPoint> {
        val xs = FloatArray(boxes.size) { boxes[it].x }
        val ys = FloatArray(boxes.size) { boxes[it].y }
        fun clamp(i: Int) {
            val b = boxes[i]
            xs[i] = xs[i].coerceIn(minOf(b.w / 2f, width / 2f), maxOf(width - b.w / 2f, width / 2f))
            ys[i] = ys[i].coerceIn(minOf(b.h / 2f, height / 2f), maxOf(height - b.h / 2f, height / 2f))
        }
        for (i in boxes.indices) clamp(i)
        repeat(iterations) {
            var moved = false
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]
                val b = boxes[j]
                val dx = xs[j] - xs[i]
                val dy = ys[j] - ys[i]
                val ox = (a.w + b.w) / 2f + gap - kotlin.math.abs(dx)
                val oy = (a.h + b.h) / 2f + gap - kotlin.math.abs(dy)
                if (ox <= 0f || oy <= 0f) continue
                moved = true
                // Separate along the axis needing the smaller push; ties/exact overlap go vertical (i up, j down).
                if (oy <= ox) {
                    val s = if (dy >= 0f) 1f else -1f
                    ys[i] -= s * oy / 2f
                    ys[j] += s * oy / 2f
                } else {
                    val s = if (dx >= 0f) 1f else -1f
                    xs[i] -= s * ox / 2f
                    xs[j] += s * ox / 2f
                }
                clamp(i)
                clamp(j)
            }
            if (!moved) return@repeat
        }
        return boxes.indices.map { ScreenPoint(xs[it], ys[it]) }
    }
}
