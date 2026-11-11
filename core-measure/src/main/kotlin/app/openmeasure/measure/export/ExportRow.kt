package app.openmeasure.measure.export

import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem

enum class ExportKind { LINE_PATH, RECTANGLE, HEIGHT }

/** Plain export record (decoupled from the Room type). [valueSi] is metres, or square metres for RECTANGLE. */
data class ExportRow(
    val createdAtMillis: Long,
    val kind: ExportKind,
    val valueSi: Double?,
    val confidence: String,
    val note: String?,
) {
    val siUnit: String get() = if (kind == ExportKind.RECTANGLE) "m2" else "m"

    fun displayValue(system: UnitSystem): String = when {
        valueSi == null -> "—"
        kind == ExportKind.RECTANGLE -> AreaFormatter.format(valueSi, system)
        else -> LengthFormatter.format(valueSi, system)
    }
}
