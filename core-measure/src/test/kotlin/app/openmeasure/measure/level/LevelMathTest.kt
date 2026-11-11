package app.openmeasure.measure.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LevelMathTest {
    private fun tilted(deg: Double) =
        UpVector(sin(Math.toRadians(deg)).toFloat(), 0f, cos(Math.toRadians(deg)).toFloat())

    @Test fun flatLevel() {
        val r = LevelMath.compute(UpVector(0f, 0f, 9.8f))
        assertEquals(LevelMode.FLAT, r.mode)
        assertTrue(r.isLevel)
        assertEquals(0f, r.tiltDeg, 1e-3f)
    }

    @Test fun flatTiltAngles() {
        val r = LevelMath.compute(tilted(3.0))
        assertEquals(3f, r.tiltDeg, 0.01f)
        assertEquals(3f, r.rollDeg, 0.01f)
        assertFalse(r.isLevel)
        assertTrue(r.bubbleX > 0f)
    }

    @Test fun faceDownAlsoLevel() {
        assertTrue(LevelMath.compute(UpVector(0f, 0f, -1f)).isLevel)
    }

    @Test fun levelThreshold() {
        assertTrue(LevelMath.isLevel(0.49f))
        assertFalse(LevelMath.isLevel(0.5f))
        assertTrue(LevelMath.isLevel(-0.3f))
    }

    @Test fun bubbleClamped() {
        val r = LevelMath.compute(tilted(40.0))
        assertEquals(1f, r.bubbleX, 1e-6f)
    }

    @Test fun modeDetectionWithHysteresis() {
        val upright = UpVector(0f, 1f, 0f)
        assertEquals(LevelMode.EDGE, LevelMath.detectMode(upright))
        val z60 = UpVector(0f, 0.6f, 0.8f) // |z|=0.8
        assertEquals(LevelMode.FLAT, LevelMath.detectMode(z60, LevelMode.FLAT))
        assertEquals(LevelMode.FLAT, LevelMath.detectMode(z60, LevelMode.EDGE))
        val mid = UpVector(0f, 0.8f, 0.6f) // |z|=0.6 between thresholds
        assertEquals(LevelMode.FLAT, LevelMath.detectMode(mid, LevelMode.FLAT))
        assertEquals(LevelMode.EDGE, LevelMath.detectMode(mid, LevelMode.EDGE))
    }

    @Test fun edgePortraitUpright() {
        val deg = 2.0
        val r = LevelMath.compute(UpVector(sin(Math.toRadians(deg)).toFloat(), cos(Math.toRadians(deg)).toFloat(), 0f))
        assertEquals(LevelMode.EDGE, r.mode)
        assertEquals(2f, r.rollDeg, 0.01f)
        assertEquals(2f, r.tiltDeg, 0.01f)
    }

    @Test fun edgeLandscapeBothSides() {
        val a = LevelMath.compute(UpVector(1f, 0f, 0f))
        val b = LevelMath.compute(UpVector(-1f, 0f, 0f))
        assertTrue(a.isLevel)
        assertTrue(b.isLevel)
        val c = LevelMath.compute(UpVector(-0.9998f, 0.0175f, 0f)) // ~1 deg
        assertEquals(1f, c.tiltDeg, 0.05f)
    }

    @Test fun lowPassConvergesAndSmooths() {
        val f = LowPassFilter(0.5f)
        assertEquals(1f, f.filter(UpVector(1f, 0f, 0f)).x, 1e-6f)
        assertEquals(0.5f, f.filter(UpVector(0f, 0f, 0f)).x, 1e-6f)
        assertEquals(0.25f, f.filter(UpVector(0f, 0f, 0f)).x, 1e-6f)
        f.reset()
        assertEquals(0f, f.filter(UpVector(0f, 0f, 0f)).x, 1e-6f)
    }
}
