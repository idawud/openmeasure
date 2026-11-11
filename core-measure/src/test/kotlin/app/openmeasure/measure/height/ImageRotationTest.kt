package app.openmeasure.measure.height

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRotationTest {
    @Test fun roundTripAllRotations() {
        for (deg in listOf(0, 90, 180, 270, -90, 450)) {
            val (xr, yr) = ImageRotation.sensorToUpright(100f, 30f, 640, 480, deg)
            val (x, y) = ImageRotation.uprightToSensor(xr, yr, 640, 480, deg)
            assertEquals(100f, x, 1e-4f)
            assertEquals(30f, y, 1e-4f)
        }
    }

    @Test fun clockwise90_topLeftGoesTopRight() {
        // Sensor 640x480 rotated 90 cw -> upright 480x640; sensor top-left lands at upright top-right.
        assertEquals(Pair(480, 640), ImageRotation.uprightSize(640, 480, 90))
        assertEquals(Pair(480f, 0f), ImageRotation.sensorToUpright(0f, 0f, 640, 480, 90))
        assertEquals(Pair(640f, 0f), ImageRotation.uprightToSensor(480f, 640f, 640, 480, 90))
        assertEquals(Pair(0f, 640f), ImageRotation.sensorToUpright(0f, 0f, 640, 480, 270))
    }

    @Test fun backCameraRotation() {
        assertEquals(90, ImageRotation.backCameraRotation(90, 0))
        assertEquals(0, ImageRotation.backCameraRotation(90, 90))
        assertEquals(180, ImageRotation.backCameraRotation(90, 270))
        assertEquals(270, ImageRotation.backCameraRotation(270, 0))
    }
}
