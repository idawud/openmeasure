package app.openmeasure.measure.snap

import app.openmeasure.measure.geometry.Vec3

/** What the reticle snapped to. UI must show this as shape + text, never colour alone. */
enum class SnapKind { NONE, EDGE, CORNER }

/**
 * Output of [EdgeSnapper.snap]. [position] (world space) and [pixelX]/[pixelY] (depth-image pixel
 * indices) are null iff [snapped] is false.
 */
data class SnapResult(
    val snapped: Boolean,
    val position: Vec3?,
    val kind: SnapKind,
    val pixelX: Int?,
    val pixelY: Int?,
    /** Distance in depth-image pixels from the requested pixel to the snapped one (0 when not snapped). */
    val pixelShift: Float,
    /** 0..1: near-side sensor confidence x edge strength x distance. */
    val confidence: Float,
    /** Camera z-depth of the snapped point in metres (0 when not snapped). */
    val depthM: Float,
) {
    fun toTarget(): SnapTarget? = if (snapped && position != null) SnapTarget(position, kind, confidence) else null

    companion object {
        val NONE = SnapResult(false, null, SnapKind.NONE, null, null, 0f, 0f, 0f)
    }
}

/** A snap target that survives across frames (world-fixed), used by [SnapHysteresis]. */
data class SnapTarget(val position: Vec3, val kind: SnapKind, val confidence: Float)
