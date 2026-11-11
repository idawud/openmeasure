package app.openmeasure.ar

import android.media.Image

/** Copies an ARCore CPU image (YUV_420_888, any row/pixel strides) into NV21 for ML Kit. */
internal object YuvToNv21 {
    /** Reuses [reuse] when it has the right size. Image dimensions must be even (ARCore CPU images are). */
    fun convert(image: Image, reuse: ByteArray?): ByteArray {
        val w = image.width
        val h = image.height
        val size = w * h * 3 / 2
        val out = reuse?.takeIf { it.size == size } ?: ByteArray(size)

        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer.duplicate()
        if (yPlane.pixelStride == 1) {
            for (row in 0 until h) {
                yBuf.position(row * yPlane.rowStride)
                yBuf.get(out, row * w, w)
            }
        } else {
            for (row in 0 until h) for (col in 0 until w) {
                out[row * w + col] = yBuf.get(row * yPlane.rowStride + col * yPlane.pixelStride)
            }
        }

        val u = image.planes[1]
        val v = image.planes[2]
        val uBuf = u.buffer.duplicate()
        val vBuf = v.buffer.duplicate()
        var o = w * h
        for (row in 0 until h / 2) {
            val uRow = row * u.rowStride
            val vRow = row * v.rowStride
            for (col in 0 until w / 2) {
                out[o++] = vBuf.get(vRow + col * v.pixelStride)
                out[o++] = uBuf.get(uRow + col * u.pixelStride)
            }
        }
        return out
    }
}
