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

    @Test fun boxRoundTrip() {
        val item = HistoryItem(
            createdAtMillis = 5L, kind = MeasurementKind.BOX, volumeCubicMeters = 0.024, confidenceLevel = "MEDIUM",
            points = List(4) { WorldPoint(it.toFloat(), 0f, 0f) },
        )
        val e = item.toEntity()
        assertEquals("BOX", e.kind)
        assertEquals(0.024, e.volumeCubicMeters!!, 0.0)
        assertEquals(item, e.toItem())
    }

    @Test fun migrationAddsNullableVolumeColumn() {
        assertEquals("ALTER TABLE `measurements` ADD COLUMN `volumeCubicMeters` REAL", AppDatabase.SQL_1_2)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test fun unknownKindFallsBack() {
        val e = MeasurementEntity(createdAtMillis = 1, kind = "FUTURE", confidenceLevel = "LOW")
        assertEquals(MeasurementKind.LINE_PATH, e.toItem().kind)
        assertNull(e.toItem().note)
    }
}
