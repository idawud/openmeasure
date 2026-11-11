package app.openmeasure.measure.export

import app.openmeasure.measure.units.UnitSystem
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** RFC 4180 CSV with CRLF line ends and spreadsheet-formula hardening. */
object CsvExporter {
    const val HEADER = "date_utc,kind,value_si,si_unit,value_display,confidence,note"

    fun toCsv(rows: List<ExportRow>, system: UnitSystem): String {
        val sb = StringBuilder(HEADER).append("\r\n")
        for (r in rows) {
            val si = r.valueSi?.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it).toPlainString() } ?: ""
            sb.append(
                listOf(isoUtc(r.createdAtMillis), r.kind.name, si, r.siUnit, r.displayValue(system), r.confidence, r.note.orEmpty())
                    .joinToString(",") { escape(it) },
            ).append("\r\n")
        }
        return sb.toString()
    }

    fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    /** Neutralises formula triggers (= + - @, tab/CR) with a leading apostrophe, then quotes if needed. */
    fun escape(cell: String): String {
        var s = cell
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'" + s
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
