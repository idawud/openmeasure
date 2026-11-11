package app.openmeasure.measure.level

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Direction of world "up" expressed in device axes (x right, y toward top of screen, z out of screen).
 * Equivalent to the normalised accelerometer reading at rest, or the 3rd row of the rotation matrix.
 */
data class UpVector(val x: Float, val y: Float, val z: Float) {
    fun normalized(): UpVector {
        val n = sqrt(x * x + y * y + z * z)
        return if (n < 1e-6f) UpVector(0f, 0f, 1f) else UpVector(x / n, y / n, z / n)
    }
}

enum class LevelMode { FLAT, EDGE }

/** Exponential low-pass filter: out += alpha * (in - out). alpha in (0,1]; smaller is smoother. */
class LowPassFilter(private val alpha: Float = 0.15f) {
    private var state: UpVector? = null

    fun filter(input: UpVector): UpVector {
        val s = state
        val out = if (s == null) input else UpVector(
            s.x + alpha * (input.x - s.x),
            s.y + alpha * (input.y - s.y),
            s.z + alpha * (input.z - s.z),
        )
        state = out
        return out
    }

    fun reset() { state = null }
}

/** Result of one level computation. Angles in degrees. */
data class LevelReading(
    val mode: LevelMode,
    /** Flat: tilt about the x axis (screen-top up/down). Edge: unused (0). */
    val pitchDeg: Float,
    /** Flat: tilt about the y axis. Edge: signed angle from level along the dominant edge. */
    val rollDeg: Float,
    /** Flat: total tilt from horizontal. Edge: abs(rollDeg). */
    val tiltDeg: Float,
    /** Bubble offset in [-1, 1] (flat mode), positive x = right, positive y = toward screen top. */
    val bubbleX: Float,
    val bubbleY: Float,
    val isLevel: Boolean,
)

object LevelMath {
    const val LEVEL_THRESHOLD_DEG = 0.5f
    /** Flat -> edge when |z| falls below this (about 60 deg from flat). */
    const val TO_EDGE_Z = 0.5f
    /** Edge -> flat when |z| rises above this (hysteresis, about 45 deg). */
    const val TO_FLAT_Z = 0.7f
    /** Tilt at which the bubble reaches the edge of the vial. */
    const val BUBBLE_FULL_SCALE_DEG = 15f

    fun detectMode(up: UpVector, previous: LevelMode = LevelMode.FLAT): LevelMode {
        val z = abs(up.normalized().z)
        return when (previous) {
            LevelMode.FLAT -> if (z < TO_EDGE_Z) LevelMode.EDGE else LevelMode.FLAT
            LevelMode.EDGE -> if (z > TO_FLAT_Z) LevelMode.FLAT else LevelMode.EDGE
        }
    }

    fun isLevel(angleDeg: Float): Boolean = abs(angleDeg) < LEVEL_THRESHOLD_DEG

    fun compute(raw: UpVector, previous: LevelMode = LevelMode.FLAT): LevelReading {
        val up = raw.normalized()
        val mode = detectMode(up, previous)
        return if (mode == LevelMode.FLAT) flat(up) else edge(up)
    }

    fun flat(up: UpVector): LevelReading {
        val pitch = Math.toDegrees(asin(up.y.coerceIn(-1f, 1f).toDouble())).toFloat()
        val roll = Math.toDegrees(asin(up.x.coerceIn(-1f, 1f).toDouble())).toFloat()
        val tilt = Math.toDegrees(acos(abs(up.z).coerceIn(0f, 1f).toDouble())).toFloat()
        // Bubble drifts toward the high side of the phone, i.e. along the up vector's xy part.
        val scale = (1.0 / kotlin.math.sin(Math.toRadians(BUBBLE_FULL_SCALE_DEG.toDouble()))).toFloat()
        val bx = (up.x * scale).coerceIn(-1f, 1f)
        val by = (up.y * scale).coerceIn(-1f, 1f)
        return LevelReading(LevelMode.FLAT, pitch, roll, tilt, bx, by, isLevel(tilt))
    }

    fun edge(up: UpVector): LevelReading {
        val angle = if (abs(up.x) >= abs(up.y)) {
            val s = if (up.x >= 0f) 1f else -1f
            atan2((up.y * s).toDouble(), abs(up.x).toDouble())
        } else {
            val s = if (up.y >= 0f) 1f else -1f
            atan2((up.x * s).toDouble(), abs(up.y).toDouble())
        }
        val deg = Math.toDegrees(angle).toFloat()
        return LevelReading(LevelMode.EDGE, 0f, deg, abs(deg), 0f, 0f, isLevel(deg))
    }
}
