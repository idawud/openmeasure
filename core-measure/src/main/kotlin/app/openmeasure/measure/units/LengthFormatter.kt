package app.openmeasure.measure.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats a length in metres for display.
 *
 * Metric: rounded to 1 cm; "84 cm" below 1 m, "1.23 m" from 1 m.
 * Imperial: rounded to 0.5 in; "7.5 in" below 12 in, "3 ft 4.5 in" from 12 in.
 *
 * NaN, infinite and negative inputs render as an em dash ("—").
 * Rounding uses BigDecimal (HALF_UP on the decimal string form) so that
 * boundaries such as 0.495 m behave as written, not as binary-float artefacts.
 */
object LengthFormatter {

    private const val DASH = "—"
    private const val METERS_PER_INCH = "0.0254"

    fun format(meters: Double, system: UnitSystem): String {
        if (!meters.isFinite() || meters < 0.0) return DASH
        return when (system) {
            UnitSystem.METRIC -> formatMetric(meters)
            UnitSystem.IMPERIAL -> formatImperial(meters)
        }
    }

    private fun formatMetric(meters: Double): String {
        val cm = BigDecimal.valueOf(meters).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return if (cm < BigDecimal(100)) {
            "${cm.toPlainString()} cm"
        } else {
            BigDecimal.valueOf(meters).setScale(2, RoundingMode.HALF_UP).toPlainString() + " m"
        }
    }

    /** Works in half-inch units: n = inches * 2, rounded to an integer. */
    private fun formatImperial(meters: Double): String {
        val inches = BigDecimal.valueOf(meters).divide(BigDecimal(METERS_PER_INCH), MathContext.DECIMAL64)
        val halfInches = inches.multiply(BigDecimal(2)).setScale(0, RoundingMode.HALF_UP).toLong()
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
