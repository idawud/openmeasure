package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Test

class LengthFormatterTest {

    private val metric = UnitSystem.METRIC
    private val imperial = UnitSystem.IMPERIAL

    private fun inchesToMeters(inches: Double) = inches * 0.0254

    // --- Metric ---

    @Test
    fun metric_belowOneMetre_showsCentimetres() {
        assertEquals("84 cm", LengthFormatter.format(0.84, metric))
        assertEquals("0 cm", LengthFormatter.format(0.0, metric))
    }

    @Test
    fun metric_fromOneMetre_showsMetresTwoDecimals() {
        assertEquals("1.23 m", LengthFormatter.format(1.234, metric))
        assertEquals("1.00 m", LengthFormatter.format(1.0, metric))
        assertEquals("12.50 m", LengthFormatter.format(12.5, metric))
    }

    @Test
    fun metric_roundsToNearestCentimetre_atHalfBoundary() {
        assertEquals("50 cm", LengthFormatter.format(0.495, metric))
        assertEquals("49 cm", LengthFormatter.format(0.494, metric))
    }

    @Test
    fun metric_centimetreRoundingCarriesToMetres() {
        // 99.5 cm rounds to 100 cm, which is shown as metres.
        assertEquals("1.00 m", LengthFormatter.format(0.995, metric))
    }

    // --- Imperial ---

    @Test
    fun imperial_belowTwelveInches_showsInchesHalfStep() {
        assertEquals("7.5 in", LengthFormatter.format(inchesToMeters(7.5), imperial))
        assertEquals("7 in", LengthFormatter.format(inchesToMeters(7.0), imperial))
    }

    @Test
    fun imperial_fromTwelveInches_showsFeetAndInches() {
        assertEquals("3 ft 4.5 in", LengthFormatter.format(inchesToMeters(40.5), imperial))
        assertEquals("1 ft 0 in", LengthFormatter.format(inchesToMeters(12.0), imperial))
    }

    @Test
    fun imperial_roundsToHalfInch() {
        assertEquals("0.5 in", LengthFormatter.format(inchesToMeters(0.25), imperial))
        assertEquals("0 in", LengthFormatter.format(inchesToMeters(0.2), imperial))
        assertEquals("7.5 in", LengthFormatter.format(inchesToMeters(7.4), imperial))
    }

    @Test
    fun imperial_carryAtTwelveInches() {
        // 11.75 in rounds to 12 in, which must carry to 1 ft 0 in.
        assertEquals("1 ft 0 in", LengthFormatter.format(inchesToMeters(11.75), imperial))
        // 11.5 in stays below the foot boundary.
        assertEquals("11.5 in", LengthFormatter.format(inchesToMeters(11.5), imperial))
    }

    // --- Fixed units ---

    @Test
    fun millimetres_wholeMillimetres_evenAboveOneMetre() {
        assertEquals("1203 mm", LengthFormatter.format(1.203, UnitSystem.MILLIMETERS))
        assertEquals("0 mm", LengthFormatter.format(0.0, UnitSystem.MILLIMETERS))
        assertEquals("0 mm", LengthFormatter.format(0.0004, UnitSystem.MILLIMETERS))
        assertEquals("1 mm", LengthFormatter.format(0.0005, UnitSystem.MILLIMETERS))
    }

    @Test
    fun centimetres_wholeCentimetres_evenAboveOneMetre() {
        assertEquals("120 cm", LengthFormatter.format(1.2, UnitSystem.CENTIMETERS))
        assertEquals("84 cm", LengthFormatter.format(0.84, UnitSystem.CENTIMETERS))
        assertEquals("50 cm", LengthFormatter.format(0.495, UnitSystem.CENTIMETERS))
    }

    @Test
    fun metres_twoDecimals_evenBelowOneMetre() {
        assertEquals("1.20 m", LengthFormatter.format(1.2, UnitSystem.METERS))
        assertEquals("0.40 m", LengthFormatter.format(0.4, UnitSystem.METERS))
        assertEquals("0.00 m", LengthFormatter.format(0.0, UnitSystem.METERS))
    }

    @Test
    fun inches_halfInchSteps_evenAboveTwelveInches() {
        assertEquals("47.5 in", LengthFormatter.format(inchesToMeters(47.5), UnitSystem.INCHES))
        assertEquals("7 in", LengthFormatter.format(inchesToMeters(7.0), UnitSystem.INCHES))
        assertEquals("0.5 in", LengthFormatter.format(inchesToMeters(0.25), UnitSystem.INCHES))
    }

    @Test
    fun feet_twoDecimals() {
        assertEquals("3.94 ft", LengthFormatter.format(1.2, UnitSystem.FEET))
        assertEquals("0.33 ft", LengthFormatter.format(0.1, UnitSystem.FEET))
        assertEquals("1.00 ft", LengthFormatter.format(0.3048, UnitSystem.FEET))
    }

    @Test
    fun fixedUnits_invalidInput_rendersDash() {
        val dash = "—"
        for (u in listOf(UnitSystem.MILLIMETERS, UnitSystem.CENTIMETERS, UnitSystem.METERS, UnitSystem.INCHES, UnitSystem.FEET)) {
            assertEquals(dash, LengthFormatter.format(Double.NaN, u))
            assertEquals(dash, LengthFormatter.format(-0.01, u))
        }
    }

    // --- Invalid input ---

    @Test
    fun invalidInput_rendersDash() {
        val dash = "—"
        assertEquals(dash, LengthFormatter.format(Double.NaN, metric))
        assertEquals(dash, LengthFormatter.format(Double.NaN, imperial))
        assertEquals(dash, LengthFormatter.format(Double.POSITIVE_INFINITY, metric))
        assertEquals(dash, LengthFormatter.format(-1.0, metric))
        assertEquals(dash, LengthFormatter.format(-0.01, imperial))
    }
}
