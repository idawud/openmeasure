package app.openmeasure.measure.capture

/** Pure helpers for photo capture (no Android types). */
object CaptureUtil {
    /** "OpenMeasure_yyyyMMdd_HHmmss.jpg" from calendar fields (caller supplies local time). */
    fun fileName(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String =
        "OpenMeasure_%04d%02d%02d_%02d%02d%02d.jpg".format(year, month, day, hour, minute, second)

    /** Returns a copy of [pixels] (row-major, [width] x [height]) with rows in reverse order (glReadPixels is bottom-up). */
    fun flipRows(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height) { "size mismatch" }
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            System.arraycopy(pixels, y * width, out, (height - 1 - y) * width, width)
        }
        return out
    }
}
