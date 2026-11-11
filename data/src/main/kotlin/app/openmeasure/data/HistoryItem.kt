package app.openmeasure.data

/** UI-facing saved measurement (domain model; maps to/from [MeasurementEntity]). */
data class HistoryItem(
    val id: Long = 0,
    val createdAtMillis: Long,
    val kind: MeasurementKind,
    val title: String? = null,
    val note: String? = null,
    val totalLengthMeters: Double? = null,
    val areaSquareMeters: Double? = null,
    val heightMeters: Double? = null,
    val confidenceLevel: String,
    val points: List<WorldPoint> = emptyList(),
    val photoUri: String? = null,
)

fun HistoryItem.toEntity() = MeasurementEntity(
    id, createdAtMillis, kind.name, title, note, totalLengthMeters, areaSquareMeters, heightMeters,
    confidenceLevel, PointsJson.encode(points), photoUri,
)

fun MeasurementEntity.toItem() = HistoryItem(
    id, createdAtMillis, MeasurementKind.entries.firstOrNull { it.name == kind } ?: MeasurementKind.LINE_PATH,
    title, note, totalLengthMeters, areaSquareMeters, heightMeters, confidenceLevel, PointsJson.decode(pointsJson), photoUri,
)
