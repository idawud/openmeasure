package app.openmeasure.ar

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Surface
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GLSurfaceView that draws the ARCore camera feed. Host must forward lifecycle via
 * [GLSurfaceView.onResume]/[GLSurfaceView.onPause]; the session itself is owned by [ArSessionManager].
 */
class ArCameraView(context: Context, private val manager: ArSessionManager) :
    GLSurfaceView(context), GLSurfaceView.Renderer {

    private val background = BackgroundRenderer()
    private var viewportChanged = true
    private var width = 0
    private var height = 0
    private var lastRotation = -1

    private val _tracking = MutableStateFlow(TrackingState.STOPPED)
    /** Camera tracking state of the latest frame. */
    val trackingState: StateFlow<TrackingState> = _tracking.asStateFlow()

    /** Latest frame, valid only until the next update; read on the GL thread via [onFrame]. */
    @Volatile var latestFrame: Frame? = null
        private set

    /** Invoked on the GL thread after each successful session.update(). */
    @Volatile var onFrame: ((Frame) -> Unit)? = null

    @Volatile private var pendingCapture: ((android.graphics.Bitmap?) -> Unit)? = null

    /** Whether at least one camera frame has been drawn (capture is refused before that). */
    @Volatile var hasFrame = false
        private set

    /** Reads the next drawn camera frame (no overlay) as a Bitmap; callback runs on the GL thread, null on failure. */
    fun captureFrame(callback: (android.graphics.Bitmap?) -> Unit) {
        if (!hasFrame) callback(null) else pendingCapture = callback
    }

    private fun readPixels(): android.graphics.Bitmap? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        return try {
            val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
            android.opengl.GLES20.glReadPixels(0, 0, w, h, android.opengl.GLES20.GL_RGBA, android.opengl.GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val rgba = IntArray(w * h)
            buf.asIntBuffer().get(rgba)
            // Memory bytes are R,G,B,A; native (little endian) int = A<<24|B<<16|G<<8|R. Swap R and B for ARGB.
            for (i in rgba.indices) {
                val c = rgba[i]
                rgba[i] = (c and 0xFF00FF00.toInt()) or ((c and 0xFF) shl 16) or ((c shr 16) and 0xFF) or (0xFF shl 24)
            }
            val flipped = app.openmeasure.measure.capture.CaptureUtil.flipRows(rgba, w, h)
            android.graphics.Bitmap.createBitmap(flipped, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (e: RuntimeException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        setWillNotDraw(false)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        background.createOnGlThread()
        manager.session?.setCameraTextureNames(intArrayOf(background.textureId))
        viewportChanged = true
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        viewportChanged = true
        android.opengl.GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT or android.opengl.GLES20.GL_DEPTH_BUFFER_BIT)
        val session = manager.session ?: return
        if (manager.state.value != ArSessionState.Running || width == 0) return
        try {
            session.setCameraTextureNames(intArrayOf(background.textureId))
            val rotation = displayRotation()
            if (rotation != lastRotation) viewportChanged = true
            if (viewportChanged) {
                lastRotation = rotation
                session.setDisplayGeometry(rotation, width, height)
                viewportChanged = false
            }
            val frame = session.update()
            latestFrame = frame
            _tracking.value = frame.camera.trackingState
            background.draw(frame)
            if (frame.timestamp != 0L) hasFrame = true
            pendingCapture?.let { cb -> pendingCapture = null; cb(readPixels()) }
            onFrame?.invoke(frame)
        } catch (e: CameraNotAvailableException) {
            _tracking.value = TrackingState.STOPPED
        } catch (e: com.google.ar.core.exceptions.SessionPausedException) {
            _tracking.value = TrackingState.PAUSED
        }
    }

    private fun displayRotation(): Int = display?.rotation ?: Surface.ROTATION_0
}
