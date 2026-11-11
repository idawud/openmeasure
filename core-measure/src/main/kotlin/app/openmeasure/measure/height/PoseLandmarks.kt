package app.openmeasure.measure.height

/** Body landmarks used for height (subset of the ML Kit / BlazePose 33-point model). */
enum class BodyLandmark {
    NOSE, LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE, LEFT_HEEL, RIGHT_HEEL, LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
}

/** One landmark in camera-image pixel-index coordinates; [inFrameLikelihood] 0..1 as reported by the pose model. */
data class Landmark2D(val x: Float, val y: Float, val inFrameLikelihood: Float)

/**
 * Landmarks of one person in the *un-rotated* camera (CPU) image of [imageWidth] x [imageHeight] pixels,
 * i.e. the same pixel space as ARCore's `Camera.getImageIntrinsics()`.
 */
data class PoseLandmarks(
    val points: Map<BodyLandmark, Landmark2D>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    operator fun get(l: BodyLandmark): Landmark2D? = points[l]
    val isEmpty: Boolean get() = points.isEmpty()

    companion object {
        fun empty(w: Int, h: Int) = PoseLandmarks(emptyMap(), w, h)
    }
}

/**
 * Pose models want an upright image, so the sensor image is passed with a clockwise rotation
 * (0/90/180/270) and landmarks come back in the rotated (upright) image. This maps them back to
 * sensor-image pixel coordinates. Continuous-coordinate convention (pixel edges at integers).
 */
object ImageRotation {
    /** Size of the upright image for a sensor image [w] x [h] rotated clockwise by [degrees]. */
    fun uprightSize(w: Int, h: Int, degrees: Int): Pair<Int, Int> =
        if (normalize(degrees) % 180 == 0) Pair(w, h) else Pair(h, w)

    /** Upright-image point ([xr], [yr]) -> sensor-image point, for a sensor image [w] x [h] rotated clockwise by [degrees]. */
    fun uprightToSensor(xr: Float, yr: Float, w: Int, h: Int, degrees: Int): Pair<Float, Float> =
        when (normalize(degrees)) {
            0 -> Pair(xr, yr)
            90 -> Pair(yr, h - xr)
            180 -> Pair(w - xr, h - yr)
            else -> Pair(w - yr, xr)
        }

    /** Sensor-image point -> upright-image point (inverse of [uprightToSensor]). */
    fun sensorToUpright(x: Float, y: Float, w: Int, h: Int, degrees: Int): Pair<Float, Float> =
        when (normalize(degrees)) {
            0 -> Pair(x, y)
            90 -> Pair(h - y, x)
            180 -> Pair(w - x, h - y)
            else -> Pair(y, w - x)
        }

    /**
     * Clockwise rotation that makes the back-camera image upright for the current display rotation
     * ([displayRotationDegrees] = Surface.ROTATION_* x 90).
     */
    fun backCameraRotation(sensorOrientationDegrees: Int, displayRotationDegrees: Int): Int =
        normalize(sensorOrientationDegrees - displayRotationDegrees)

    private fun normalize(d: Int): Int {
        require(d % 90 == 0) { "rotation must be a multiple of 90, was $d" }
        return ((d % 360) + 360) % 360
    }
}
