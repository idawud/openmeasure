package app.openmeasure

import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.guide.GuideMath
import app.openmeasure.measure.guide.GuideState
import app.openmeasure.measure.guide.GuideTracker
import app.openmeasure.measure.projection.ScreenSegment

/** Guide overlay data in view pixels; [level]/[plumb] are the dashed references through the start point. */
data class GuideUi(
    val state: GuideState,
    val level: ScreenSegment?,
    val plumb: ScreenSegment?,
    /** Deviation in degrees from the nearer axis (informational). */
    val deviationDeg: Float?,
)

/** F12 helper for [MeasureController]: tracks alignment of the live segment and computes the locked end. */
internal class GuideController {
    private val tracker = GuideTracker()
    var acquireTick = 0
        private set

    class Result(val end: Vec3, val ui: GuideUi, val locked: Boolean)

    /** [enabled] = Settings "Guide lines", line mode and tracking. Returns null when there is nothing to guide. */
    fun update(start: Vec3?, end: Vec3?, enabled: Boolean, f: FrameView): Result? {
        if (!enabled || start == null || end == null) {
            tracker.reset()
            return null
        }
        val state = tracker.update(start, end)
        if (tracker.acquired) acquireTick++
        val d = end - start
        val horiz = Vec3(d.x, 0f, d.z)
        val level = if (horiz.length() > 1e-3f) f.segment(start, start + horiz.normalized() * REF_LEN_M) else null
        val plumb = f.segment(start - Vec3(0f, REF_LEN_M, 0f), start + Vec3(0f, REF_LEN_M, 0f))
        val ui = GuideUi(state, level, plumb, tracker.deviationDeg)
        return Result(GuideMath.alignedEnd(start, end, state), ui, state != GuideState.NONE)
    }

    private companion object {
        const val REF_LEN_M = 3f
    }
}
