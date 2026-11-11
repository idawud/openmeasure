package app.openmeasure.measure.export

import app.openmeasure.measure.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test fun headerAndRow() {
        val csv = CsvExporter.toCsv(listOf(ExportRow(0L, ExportKind.LINE_PATH, 1.234, "HIGH", "desk")), UnitSystem.METRIC)
        val lines = csv.split("\r\n")
        assertEquals(CsvExporter.HEADER, lines[0])
        assertEquals("1970-01-01T00:00:00Z,LINE_PATH,1.234,m,1.23 m,HIGH,desk", lines[1])
    }

    @Test fun imperialAndArea() {
        val csv = CsvExporter.toCsv(listOf(ExportRow(0L, ExportKind.RECTANGLE, 1.245, "LOW", null)), UnitSystem.METRIC)
        assertTrue(csv.contains(",RECTANGLE,1.245,m2,1.25 m²,LOW,\r\n"))
        val imp = CsvExporter.toCsv(listOf(ExportRow(0L, ExportKind.HEIGHT, 0.3048, "HIGH", null)), UnitSystem.IMPERIAL)
        assertTrue(imp.contains("1 ft 0 in"))
    }

    @Test fun nullValueIsEmptySi() {
        val csv = CsvExporter.toCsv(listOf(ExportRow(0L, ExportKind.HEIGHT, null, "HIGH", null)), UnitSystem.METRIC)
        assertTrue(csv.contains(",HEIGHT,,m,—,HIGH,"))
    }

    @Test fun rfc4180Escaping() {
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"l1\nl2\"", CsvExporter.escape("l1\nl2"))
        assertEquals("plain", CsvExporter.escape("plain"))
    }

    @Test fun injectionHardening() {
        assertEquals("'=SUM(A1)", CsvExporter.escape("=SUM(A1)"))
        assertEquals("'+1", CsvExporter.escape("+1"))
        assertEquals("'-1", CsvExporter.escape("-1"))
        assertEquals("'@x", CsvExporter.escape("@x"))
        assertEquals("\"'=1,2\"", CsvExporter.escape("=1,2"))
        assertEquals("a=b", CsvExporter.escape("a=b"))
    }

    @Test fun emptyListHeaderOnly() {
        assertEquals(CsvExporter.HEADER + "\r\n", CsvExporter.toCsv(emptyList(), UnitSystem.METRIC))
    }
}
