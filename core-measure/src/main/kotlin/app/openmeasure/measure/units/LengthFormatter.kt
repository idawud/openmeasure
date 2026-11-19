package app.openmeasure.measure.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats a length in metres for display.
 *
 * Metric (auto): rounded to 1 cm; "84 cm" below 1 m, "1.23 m" from 1 m.
 * Imperial (auto): rounded to 0.5 in; "7.5 in" below 12 in, "3 ft 4.5 in" from 12 in.
 * Fixed units: "1203 mm" (whole mm), "120 cm" (whole cm), "1.20 m" (2 decimals),
 * "47.5 in" (nearest 0.5 in), "3.94 ft" (2 decimals). Fixed units never switch to another unit.
 *
 * NaN, infinite and negative inputs render as an em dash ("—").
 * Rounding uses BigDecimal (HALF_UP on the decimal string form) so that
 * boundaries such as 0.495 m behave as written, not as binary-float artefacts.
 */
object LengthFormatter {

    private const val DASH = "—"
    private const val METERS_PER_INCH = "0.0254"
    private const val METERS_PER_FOOT = "0.3048"

    fun format(meters: Double, system: UnitSystem): String {
        if (!meters.isFinite() || meters < 0.0) return DASH
        return when (system) {
            UnitSystem.METRIC -> formatMetric(meters)
            UnitSystem.IMPERIAL -> formatImperial(meters)
            UnitSystem.MILLIMETERS -> formatMillimetres(meters)
            UnitSystem.CENTIMETERS -> formatCentimetres(meters)
            UnitSystem.METERS -> formatMetres(meters)
            UnitSystem.INCHES -> formatInchesOnly(meters)
            UnitSystem.FEET -> formatFeetOnly(meters)
        }
    }

    private fun formatMetric(meters: Double): String {
        val cm = BigDecimal.valueOf(meters).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return if (cm < BigDecimal(100)) {
            "${cm.toPlainString()} cm"
        } else {
            formatMetres(meters)
        }
    }

    private fun formatMillimetres(meters: Double): String {
        val mm = BigDecimal.valueOf(meters).movePointRight(3).setScale(0, RoundingMode.HALF_UP)
        return "${mm.toPlainString()} mm"
    }

    private fun formatCentimetres(meters: Double): String {
        val cm = BigDecimal.valueOf(meters).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return "${cm.toPlainString()} cm"
    }

    private fun formatMetres(meters: Double): String =
        BigDecimal.valueOf(meters).setScale(2, RoundingMode.HALF_UP).toPlainString() + " m"

    /** Whole half-inch rounding, but never switches to feet. */
    private fun formatInchesOnly(meters: Double): String {
        val halfInches = halfInches(meters)
        return "${formatInches(halfInches)} in"
    }

    private fun formatFeetOnly(meters: Double): String {
        val feet = BigDecimal.valueOf(meters).divide(BigDecimal(METERS_PER_FOOT), MathContext.DECIMAL64)
        return feet.setScale(2, RoundingMode.HALF_UP).toPlainString() + " ft"
    }

    private fun halfInches(meters: Double): Long {
        val inches = BigDecimal.valueOf(meters).divide(BigDecimal(METERS_PER_INCH), MathContext.DECIMAL64)
        return inches.multiply(BigDecimal(2)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Works in half-inch units: n = inches * 2, rounded to an integer. */
    private fun formatImperial(meters: Double): String {
        val halfInches = halfInches(meters)
        return if (halfInches < 24L) {
            "${formatInches(halfInches)} in"
        } else {
            val feet = halfInches / 24L
            val remainder = halfInches % 24L
            "$feet ft ${formatInches(remainder)} in"
        }
    }

    /** Renders a half-inch count as "7", "7.5" (whole inches with optional .5). */
    private fun formatInches(halfInches: Long): String {
        val whole = halfInches / 2L
        return if (halfInches % 2L == 1L) "$whole.5" else "$whole"
    }
}
