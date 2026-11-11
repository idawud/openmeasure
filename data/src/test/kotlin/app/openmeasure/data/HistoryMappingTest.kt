package app.openmeasure.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMappingTest {
    @Test fun pointsRoundTrip() {
        val pts = listOf(WorldPoint(1.5f, -2f, 0.25f), WorldPoint(-0.001f, 3e-5f, 100f))
        assertEquals(pts, PointsJson.decode(PointsJson.encode(pts)))
    }

    @Test fun emptyAndMalformed() {
        assertEquals("[]", PointsJson.encode(emptyList()))
        assertEquals(emptyList<WorldPoint>(), PointsJson.decode("[]"))
        assertEquals(emptyList<WorldPoint>(), PointsJson.decode("garbage"))
        assertEquals(emptyList<WorldPoint>(), PointsJson.decode("[[1,2]]"))
        assertEquals(emptyList<WorldPoint>(), PointsJson.decode("[[1,2,x]]"))
        assertEquals(emptyList<WorldPoint>(), PointsJson.decode("[[1,2,3"))
    }

    @Test fun entityRoundTrip() {
        val item = HistoryItem(
            id = 7, createdAtMillis = 123L, kind = MeasurementKind.RECTANGLE, note = "desk",
            areaSquareMeters = 1.25, confidenceLevel = "HIGH",
            points = listOf(WorldPoint(0f, 0f, 0f), WorldPoint(1f, 0f, 0f)), photoUri = "content://x/1",
        )
        assertEquals(item, item.toEntity().toItem())
    }

    @Test fun unknownKindFallsBack() {
        val e = MeasurementEntity(createdAtMillis = 1, kind = "FUTURE", confidenceLevel = "LOW")
        assertEquals(MeasurementKind.LINE_PATH, e.toItem().kind)
        assertNull(e.toItem().note)
    }
}
