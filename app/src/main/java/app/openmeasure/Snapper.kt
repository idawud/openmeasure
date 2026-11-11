package app.openmeasure

import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArHit
import app.openmeasure.ar.HitTestService
import app.openmeasure.ar.SnapService
import app.openmeasure.measure.snap.SnapHysteresis
import app.openmeasure.measure.snap.SnapKind
import com.google.ar.core.Frame

/** Edge snapping shared by reticle and drag: settings, hysteresis and the acquire tick used for haptics. */
internal class Snapper(service: HitTestService, private val view: ArCameraView) {
    private val snapService = SnapService(service.depthSource)
    /** Settings "Snap to edges"; read on the GL thread every frame. */
    @Volatile var enabled: Boolean = true
    /** Max snap distance in view pixels (set from dp by the UI). Snapped points never move farther. */
    @Volatile var radiusPx: Float = 60f
    var tick = 0
        private set
    var reticle = newHysteresis()
        private set
    var drag = newHysteresis()
        private set
    private var hystRadius = radiusPx

    /** Rebuild hysteresis when the radius setting changed. */
    fun syncRadius() {
        if (hystRadius != radiusPx) {
            hystRadius = radiusPx
            reticle = newHysteresis()
            drag = newHysteresis()
        }
    }

    /** Release snaps at the max radius and acquire only well inside it, so a held target never exceeds it. */
    private fun newHysteresis(): SnapHysteresis {
        val r = radiusPx.coerceAtLeast(1f)
        return SnapHysteresis(acquireRadiusPx = r * 0.6f, releaseRadiusPx = r)
    }

    /** Applies snapping (if enabled) to [base] hit at view (x, y); counts acquire ticks for haptics. */
    fun snap(frame: Frame, x: Float, y: Float, base: ArHit, hyst: SnapHysteresis): SnapService.Outcome {
        if (!enabled) {
            hyst.reset()
            return SnapService.Outcome(base, SnapKind.NONE, false, null)
        }
        val out = snapService.snap(frame, x, y, base, hyst, view.width, view.height, radiusPx)
        if (out.acquired) tick++
        return out
    }
}
