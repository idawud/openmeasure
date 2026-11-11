package app.openmeasure.measure.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Builds spoken (TalkBack) phrases for lengths and angles, e.g. "84 centimetres",
 * "1.2 metres", "3 feet 4 inches", "Line" is added by the caller.
 *
 * Same rounding as [LengthFormatter] (1 cm metric, half inch imperial) but spelled out,
 * with singular forms only for an amount of exactly one.
 * Returns null for NaN, infinite or negative lengths so callers can show a fallback.
 */
object SpokenLength {

    private const val METERS_PER_INCH = "0.0254"

    fun speak(meters: Double, system: UnitSystem): String? {
        if (!meters.isFinite() || meters < 0.0) return null
        return when (system) {
            UnitSystem.METRIC -> metric(meters)
            UnitSystem.IMPERIAL -> imperial(meters)
        }
    }

    /**
     * Spoken angle rounded to one decimal, e.g. "2.5 degrees", "2 degrees", "1 degree".
     * The sign is ignored because level readings are phrased as "off". Null for NaN or infinite.
     */
    fun speakDegrees(degrees: Double): String? {
        if (!degrees.isFinite()) return null
        val tenths = BigDecimal.valueOf(kotlin.math.abs(degrees))
            .setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return amount(tenths, "degree", "degrees")
    }

    private fun metric(meters: Double): String {
        val cm = BigDecimal.valueOf(meters).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        return if (cm < 100L) {
            amount(cm.toString(), "centimetre", "centimetres")
        } else {
            val m = BigDecimal.valueOf(meters).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            amount(m, "metre", "metres")
        }
    }

    /** Works in half-inch units: n = inches * 2, rounded to an integer. */
    private fun imperial(meters: Double): String {
        val inches = BigDecimal.valueOf(meters).divide(BigDecimal(METERS_PER_INCH), MathContext.DECIMAL64)
        val halfInches = inches.multiply(BigDecimal(2)).setScale(0, RoundingMode.HALF_UP).toLong()
        if (halfInches < 24L) return inchesPhrase(halfInches)
        val feet = halfInches / 24L
        val remainder = halfInches % 24L
        val feetPart = amount(feet.toString(), "foot", "feet")
        return if (remainder == 0L) feetPart else "$feetPart ${inchesPhrase(remainder)}"
    }

    /** Renders a half-inch count as "1 inch", "0.5 inches", "1.5 inches", "0 inches". */
    private fun inchesPhrase(halfInches: Long): String {
        val whole = halfInches / 2L
        val text = if (halfInches % 2L == 1L) "$whole.5" else "$whole"
        return amount(text, "inch", "inches")
    }

    private fun amount(text: String, singular: String, plural: String): String =
        if (text == "1") "$text $singular" else "$text $plural"
}
