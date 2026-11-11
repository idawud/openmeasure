package app.openmeasure.measure.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats and speaks a volume given in cubic metres (F14).
 *
 * Metric: whole cm³ below 1000 cm³ ("450 cm³"), else m³ with 3 decimals ("0.024 m³").
 *   [litres] gives the companion "24 L" (one decimal below 10 L, whole litres above).
 * Imperial: whole in³ below 1728 in³ (1 ft³) ("300 in³"), else ft³ with 2 decimals ("2.50 ft³").
 * The unit is chosen after rounding (999.6 cm³ reads "0.001 m³"). NaN, infinite and negative inputs
 * format as "—" and speak as null. Rounding is BigDecimal HALF_UP.
 */
object VolumeFormatter {
    private const val DASH = "—"
    private const val CUBIC_M_PER_CUBIC_IN = "0.000016387064" // 0.0254^3 exactly
    private val CM3_PER_M3 = BigDecimal(1_000_000)
    private val THOUSAND = BigDecimal(1000)
    private val TEN = BigDecimal(10)
    private val IN3_PER_FT3 = BigDecimal(1728)

    fun format(cubicMeters: Double, system: UnitSystem): String {
        val p = parts(cubicMeters, system) ?: return DASH
        return "${p.amount} ${p.symbol}"
    }

    /** "24 L", "2.5 L", "0.4 L"; "—" for invalid input. */
    fun litres(cubicMeters: Double): String = litreAmount(cubicMeters)?.let { "$it L" } ?: DASH

    /** TalkBack phrase, e.g. "0.024 cubic metres, 24 litres", "450 cubic centimetres", "2.5 cubic feet"; null if invalid. */
    fun speak(cubicMeters: Double, system: UnitSystem): String? {
        val p = parts(cubicMeters, system) ?: return null
        val main = amount(trim(p.amount), p.singular, p.plural)
        if (system != UnitSystem.METRIC || p.symbol != "m³") return main
        val l = litreAmount(cubicMeters) ?: return main
        return "$main, ${amount(trim(l), "litre", "litres")}"
    }

    private class Parts(val amount: String, val symbol: String, val singular: String, val plural: String)

    private fun parts(cubicMeters: Double, system: UnitSystem): Parts? {
        if (!cubicMeters.isFinite() || cubicMeters < 0.0) return null
        val m3 = BigDecimal.valueOf(cubicMeters)
        return when (system) {
            UnitSystem.METRIC -> {
                val cm3 = m3.multiply(CM3_PER_M3).setScale(0, RoundingMode.HALF_UP)
                if (cm3 < THOUSAND) Parts(cm3.toPlainString(), "cm³", "cubic centimetre", "cubic centimetres")
                else Parts(m3.setScale(3, RoundingMode.HALF_UP).toPlainString(), "m³", "cubic metre", "cubic metres")
            }
            UnitSystem.IMPERIAL -> {
                val in3 = m3.divide(BigDecimal(CUBIC_M_PER_CUBIC_IN), MathContext.DECIMAL64)
                val in3Rounded = in3.setScale(0, RoundingMode.HALF_UP)
                if (in3Rounded < IN3_PER_FT3) Parts(in3Rounded.toPlainString(), "in³", "cubic inch", "cubic inches")
                else Parts(
                    in3.divide(IN3_PER_FT3, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "ft³", "cubic foot", "cubic feet",
                )
            }
        }
    }

    private fun litreAmount(cubicMeters: Double): String? {
        if (!cubicMeters.isFinite() || cubicMeters < 0.0) return null
        val l = BigDecimal.valueOf(cubicMeters).multiply(THOUSAND)
        val tenths = l.setScale(1, RoundingMode.HALF_UP)
        return if (tenths < TEN) tenths.toPlainString() else l.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }

    private fun trim(s: String) = if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s

    private fun amount(text: String, singular: String, plural: String) = if (text == "1") "$text $singular" else "$text $plural"
}
