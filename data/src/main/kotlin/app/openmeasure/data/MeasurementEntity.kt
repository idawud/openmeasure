package app.openmeasure.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What was measured. Stored by name so reordering the enum never corrupts old rows. */
enum class MeasurementKind { LINE_PATH, RECTANGLE, HEIGHT, BOX }

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    /** [MeasurementKind] name. */
    val kind: String,
    val title: String? = null,
    val note: String? = null,
    val totalLengthMeters: Double? = null,
    val areaSquareMeters: Double? = null,
    val heightMeters: Double? = null,
    /** ConfidenceLevel name (HIGH / MEDIUM / LOW). */
    val confidenceLevel: String,
    /** World points as `[[x,y,z],...]`, see [PointsJson]. */
    val pointsJson: String = "[]",
    val photoUri: String? = null,
    /** BOX volume in m³ (added in schema v2). */
    val volumeCubicMeters: Double? = null,
)
