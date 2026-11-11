package app.openmeasure.measure.angle

import app.openmeasure.measure.confidence.ConfidenceInput
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.ConfidenceResult
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.units.SpokenLength
import java.math.BigDecimal
import java.math.RoundingMode

/** What the angle label may show. A number only ever appears inside [Angle]. */
sealed interface AngleContent {
    data class Angle(val degrees: Double, val level: ConfidenceLevel, val score: Float) : AngleContent
    data class Hint(val reason: ConfidenceReason) : AngleContent
}

object AngleLabel {
    /**
     * Angle at [vPos] between arms to [aPos] and [bPos], gated by the weakest of the three endpoints ([ConfidenceScorer]).
     * Null when the angle is undefined or an arm is shorter than [AngleMath.MIN_ARM_M].
     */
    fun build(
        tracking: TrackingStatus,
        a: EndpointObservation, v: EndpointObservation, b: EndpointObservation,
        aPos: Vec3, vPos: Vec3, bPos: Vec3,
    ): AngleContent? {
        if (aPos.distanceTo(vPos) < AngleMath.MIN_ARM_M || bPos.distanceTo(vPos) < AngleMath.MIN_ARM_M) return null
        val degrees = AngleMath.angleDegrees(aPos, vPos, bPos) ?: return null
        val r1 = ConfidenceScorer.score(ConfidenceInput(tracking, a, v))
        val r2 = ConfidenceScorer.score(ConfidenceInput(tracking, v, b))
        val worst = if (weaker(r1, r2)) r1 else r2
        if (!worst.canDisplayNumber) return AngleContent.Hint(worst.reason)
        return AngleContent.Angle(degrees, worst.level, worst.score)
    }

    private fun weaker(x: ConfidenceResult, y: ConfidenceResult): Boolean =
        if (x.level != y.level) x.level.ordinal > y.level.ordinal else x.score <= y.score

    /** "72.5°" (one decimal, half-up; locale independent). */
    fun format(degrees: Double): String =
        BigDecimal.valueOf(degrees).setScale(1, RoundingMode.HALF_UP).toPlainString() + "°"

    /** "Angle, 90 degrees"; null for non-finite input. */
    fun speak(degrees: Double): String? = SpokenLength.speakDegrees(degrees)?.let { "Angle, $it" }
}
