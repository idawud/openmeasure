package app.openmeasure.measure.export

import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter

enum class ExportKind { LINE_PATH, RECTANGLE, HEIGHT, BOX }

/** Plain export record (decoupled from the Room type). [valueSi] is metres, m² for RECTANGLE, m³ for BOX. */
data class ExportRow(
    val createdAtMillis: Long,
    val kind: ExportKind,
    val valueSi: Double?,
    val confidence: String,
    val note: String?,
) {
    val siUnit: String get() = when (kind) {
        ExportKind.RECTANGLE -> "m2"
        ExportKind.BOX -> "m3"
        else -> "m"
    }

    fun displayValue(system: UnitSystem): String = when {
        valueSi == null -> "—"
        kind == ExportKind.RECTANGLE -> AreaFormatter.format(valueSi, system)
        kind == ExportKind.BOX -> VolumeFormatter.format(valueSi, system)
        else -> LengthFormatter.format(valueSi, system)
    }
}
