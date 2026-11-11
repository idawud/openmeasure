package app.openmeasure.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Draws the ARCore camera image as a full-screen quad using an external OES texture. Call on the GL thread. */
class BackgroundRenderer {
    var textureId = 0
        private set
    private var program = 0
    private var posLoc = 0
    private var uvLoc = 0
    private var texLoc = 0

    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uvIn: FloatBuffer = floatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
    private val uvOut: FloatBuffer = floatBuffer(FloatArray(8))

    fun createOnGlThread() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        posLoc = GLES20.glGetAttribLocation(program, "aPos")
        uvLoc = GLES20.glGetAttribLocation(program, "aUv")
        texLoc = GLES20.glGetUniformLocation(program, "uTex")
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0)
            uvOut.position(0)
            frame.transformCoordinates2d(
                com.google.ar.core.Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                com.google.ar.core.Coordinates2d.TEXTURE_NORMALIZED, uvOut,
            )
        }
        if (frame.timestamp == 0L || program == 0) return
        uvOut.position(0)
        quad.position(0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(texLoc, 0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, uvOut)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compile(type: Int, src: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }

    private companion object {
        fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data)
                position(0)
            }

        const val VERTEX = """
            attribute vec4 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { gl_Position = aPos; vUv = aUv; }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv;
            uniform samplerExternalOES uTex;
            void main() { gl_FragColor = texture2D(uTex, vUv); }
        """
    }
}
