package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3

/** A buffered value (e.g. a hit) picked for placement, with the sample's time and camera speed. */
class Pick<T>(val value: T, val tMs: Long, val speedMps: Float)

/**
 * Short history of per-frame hits so a tap can use the hit the user actually aimed at, not the one after the
 * phone has shaken. [pick] returns the newest sample at least [lookbackMs] old whose camera was moving no faster
 * than [maxSpeedMps]. Time is injected (any monotonic ms clock). Not thread-safe; use on one thread (GL).
 */
class PlacementBuffer<T>(
    val capacityMs: Long = 400,
    val lookbackMs: Long = 120,
    val maxSpeedMps: Float = 0.25f,
) {
    private class Sample<T>(val tMs: Long, val camPos: Vec3, val speedMps: Float, val value: T?)

    private val samples = ArrayDeque<Sample<T>>()

    /** Camera speed (m/s) at the latest sample; 0 when empty. */
    val currentSpeed: Float get() = samples.lastOrNull()?.speedMps ?: 0f

    /** Records one frame. [value] null means no valid hit this frame. Speed is derived from the previous camPos. */
    fun record(tMs: Long, camPos: Vec3, value: T?) {
        val prev = samples.lastOrNull()
        val speed = if (prev == null) 0f else {
            val dt = (tMs - prev.tMs) / 1000f
            if (dt <= 0f) prev.speedMps else camPos.distanceTo(prev.camPos) / dt
        }
        samples.addLast(Sample(tMs, camPos, speed, value))
        while (samples.isNotEmpty() && samples.first().tMs < tMs - capacityMs) samples.removeFirst()
    }

    /**
     * Newest sample with a non-null value, `t <= nowMs - lookbackMs` and speed `<= maxSpeedMps`; null if none.
     */
    fun pick(nowMs: Long): Pick<T>? {
        val cutoff = nowMs - lookbackMs
        for (i in samples.indices.reversed()) {
            val s = samples[i]
            if (s.tMs > cutoff) continue
            val v = s.value ?: continue
            if (s.speedMps <= maxSpeedMps) return Pick(v, s.tMs, s.speedMps)
        }
        return null
    }

    /** Camera is slow enough to trust the live hit (looser than [pick]'s limit). */
    fun isSteady(): Boolean = currentSpeed <= maxSpeedMps * 2

    fun clear() = samples.clear()
}
