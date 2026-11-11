package app.openmeasure.measure.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats and speaks an area given in square metres (F9).
 *
 * Metric: whole cm² below 1 m² ("600 cm²"), else m² with 2 decimals ("1.25 m²").
 * Imperial: whole in² below 1 ft² ("96 in²"), else ft² with 1 decimal ("12.5 ft²").
 * The unit is chosen after rounding, so 0.99996 m² reads "1.00 m²", never "10000 cm²".
 * NaN, infinite and negative inputs format as "—" and speak as null. Rounding is BigDecimal HALF_UP.
 */
object AreaFormatter {

    private const val DASH = "—"
    private const val SQ_M_PER_SQ_IN = "0.00064516" // 0.0254^2 exactly
    private val CM2_PER_M2 = BigDecimal(10_000)
    private val IN2_PER_FT2 = BigDecimal(144)

    fun format(squareMeters: Double, system: UnitSystem): String {
        val a = parts(squareMeters, system) ?: return DASH
        return "${a.amount} ${a.symbol}"
    }

    /** TalkBack phrase, e.g. "600 square centimetres", "1 square metre", "12.5 square feet"; null if invalid. */
    fun speak(squareMeters: Double, system: UnitSystem): String? {
        val a = parts(squareMeters, system) ?: return null
        val spoken = a.amount.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
        return if (spoken == "1") "$spoken ${a.singular}" else "$spoken ${a.plural}"
    }

    private class Parts(val amount: String, val symbol: String, val singular: String, val plural: String)

    private fun parts(squareMeters: Double, system: UnitSystem): Parts? {
        if (!squareMeters.isFinite() || squareMeters < 0.0) return null
        val m2 = BigDecimal.valueOf(squareMeters)
        return when (system) {
            UnitSystem.METRIC -> {
                val cm2 = m2.multiply(CM2_PER_M2).setScale(0, RoundingMode.HALF_UP)
                if (cm2 < CM2_PER_M2) Parts(cm2.toPlainString(), "cm²", "square centimetre", "square centimetres")
                else Parts(m2.setScale(2, RoundingMode.HALF_UP).toPlainString(), "m²", "square metre", "square metres")
            }
            UnitSystem.IMPERIAL -> {
                val in2 = m2.divide(BigDecimal(SQ_M_PER_SQ_IN), MathContext.DECIMAL64)
                val in2Rounded = in2.setScale(0, RoundingMode.HALF_UP)
                if (in2Rounded < IN2_PER_FT2) Parts(in2Rounded.toPlainString(), "in²", "square inch", "square inches")
                else Parts(
                    in2.divide(IN2_PER_FT2, MathContext.DECIMAL64).setScale(1, RoundingMode.HALF_UP).toPlainString(),
                    "ft²", "square foot", "square feet",
                )
            }
        }
    }
}
