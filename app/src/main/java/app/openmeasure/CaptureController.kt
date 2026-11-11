package app.openmeasure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import app.openmeasure.ar.ArCameraView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Grabs the camera frame, composites the overlay layer on top and saves it. */
object CaptureController {
    /** Returns the saved Uri, or null when no frame is available or saving failed. */
    suspend fun captureAndSave(context: Context, camera: ArCameraView, overlay: GraphicsLayer): Uri? {
        val frame = suspendCancellableCoroutine<Bitmap?> { cont -> camera.captureFrame { cont.resume(it) } } ?: return null
        val overlayBmp = try { overlay.toImageBitmap().asAndroidBitmap() } catch (e: Exception) { null }
        return withContext(Dispatchers.IO) {
            val out = frame.copy(Bitmap.Config.ARGB_8888, true)
            frame.recycle()
            if (overlayBmp != null) {
                val dst = android.graphics.Rect(0, 0, out.width, out.height)
                Canvas(out).drawBitmap(overlayBmp, null, dst, null)
            }
            val uri = ShareHelper.saveJpeg(context, out)
            out.recycle()
            uri
        }
    }
}
