package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Vec3

/**
 * Pinhole intrinsics in pixel-index coordinates (pixel i's centre is at i) for an image of
 * [width] x [height]. Matches ARCore CameraIntrinsics (focal length + principal point in pixels).
 */
data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val width: Int,
    val height: Int,
) {
    /**
     * Rescales to another resolution of the same sensor crop (e.g. CPU image 640x480 -> depth 160x120).
     * Uses the pixel-centre convention: x' + 0.5 = (x + 0.5) * s.
     */
    fun scaledTo(newWidth: Int, newHeight: Int): CameraIntrinsics {
        val sx = newWidth.toFloat() / width
        val sy = newHeight.toFloat() / height
        return CameraIntrinsics(
            fx = fx * sx,
            fy = fy * sy,
            cx = (cx + 0.5f) * sx - 0.5f,
            cy = (cy + 0.5f) * sy - 0.5f,
            width = newWidth,
            height = newHeight,
        )
    }

    /** Normalised image coords (0..1, top-left origin) -> pixel-index coords in this image. */
    fun normalizedToPixel(nx: Float, ny: Float): Pair<Float, Float> =
        Pair(nx * width - 0.5f, ny * height - 0.5f)
}

/**
 * Row-major depth image. [depthMm] holds z-depth along the optical axis in millimetres (0 = invalid),
 * exactly as ARCore's acquireDepthImage16Bits. [confidence] (optional, same size) holds 0..255 per
 * pixel as in acquireRawDepthConfidenceImage.
 */
class DepthMap(
    val width: Int,
    val height: Int,
    val depthMm: ShortArray,
    val confidence: ByteArray? = null,
) {
    init {
        require(depthMm.size == width * height) { "depth size ${depthMm.size} != ${width}x$height" }
        require(confidence == null || confidence.size == width * height) { "confidence size mismatch" }
    }

    fun depthAt(x: Int, y: Int): Int = depthMm[y * width + x].toInt() and 0xFFFF
    fun confidenceAt(x: Int, y: Int): Int? = confidence?.let { it[y * width + x].toInt() and 0xFF }
}

/** A world-space point sampled from the depth map. */
data class DepthHit(
    val position: Vec3,
    /** Camera z-depth in metres (median over the patch). */
    val depthM: Float,
    /** 0..1 combined depth confidence. */
    val confidence: Float,
    /** Fraction of valid depth samples in the patch. */
    val validFraction: Float,
)
