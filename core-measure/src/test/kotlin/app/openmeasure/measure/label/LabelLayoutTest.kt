package app.openmeasure.measure.label

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelLayoutTest {
    private fun overlap(a: LabelBox, ap: app.openmeasure.measure.projection.ScreenPoint,
                        b: LabelBox, bp: app.openmeasure.measure.projection.ScreenPoint): Boolean =
        kotlin.math.abs(ap.x - bp.x) < (a.w + b.w) / 2f && kotlin.math.abs(ap.y - bp.y) < (a.h + b.h) / 2f

    @Test fun nonOverlappingUnchanged() {
        val boxes = listOf(LabelBox(100f, 100f, 50f, 30f), LabelBox(300f, 300f, 50f, 30f))
        val r = LabelLayout.resolve(boxes, 1000f, 1000f)
        assertEquals(100f, r[0].x, 0f); assertEquals(300f, r[1].y, 0f)
    }

    @Test fun identicalPositionsSeparated() {
        val boxes = List(3) { LabelBox(500f, 500f, 80f, 40f) }
        val r = LabelLayout.resolve(boxes, 1000f, 1000f)
        for (i in 0 until 3) for (j in i + 1 until 3) assertTrue(!overlap(boxes[i], r[i], boxes[j], r[j]))
    }

    @Test fun clampedInsideView() {
        val r = LabelLayout.resolve(listOf(LabelBox(-50f, 2000f, 100f, 40f)), 400f, 800f)
        assertEquals(50f, r[0].x, 0f); assertEquals(780f, r[0].y, 0f)
    }

    @Test fun emptyInput() { assertTrue(LabelLayout.resolve(emptyList(), 100f, 100f).isEmpty()) }

    @Test fun totalOnlyWhenAllDisplayable() {
        val ok = LabelContent.Length(1.0, ConfidenceLevel.HIGH, 0.9f)
        val ok2 = LabelContent.Length(2.5, ConfidenceLevel.MEDIUM, 0.6f)
        assertNull(PathTotal.of(listOf(ok)))
        assertEquals(3.5, PathTotal.of(listOf(ok, ok2))!!, 1e-9)
        assertNull(PathTotal.of(listOf(ok, LabelContent.Hint(ConfidenceReason.TRACKING_POOR))))
        assertNotNull(PathTotal.of(listOf(ok, ok)))
    }
}
