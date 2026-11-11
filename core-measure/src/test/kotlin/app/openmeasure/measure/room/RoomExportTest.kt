package app.openmeasure.measure.room

import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RoomLabel gating, PlanLayout fitting and PlanSvg export. */
class RoomExportTest {
    private val room = listOf(Vec3(0f, 0f, 0f), Vec3(4f, 0f, 0f), Vec3(4f, 0f, -3f), Vec3(0f, 0f, -3f))
    private val plan = RoomPlan.fromCorners(room)
    private val good = EndpointObservation(HitSource.DEPTH, 0.95f, 1.5f)

    @Test fun labelsShowNumbersWhenConfident() {
        val l = RoomLabel.build(TrackingStatus.TRACKING, List(4) { good }, plan)
        assertEquals(4, l.walls.size)
        assertEquals(4.0, (l.walls[0] as LabelContent.Length).meters, 1e-4)
        val a = l.area as AreaContent.Area
        assertEquals(12.0, a.squareMeters, 1e-4)
        assertTrue(a.isRectangle)
        assertEquals(ConfidenceLevel.HIGH, l.level)
        assertNull(l.ceiling)
        assertNull(l.volume)
    }

    @Test fun weakestCornerGatesAreaButNotOtherWalls() {
        val obs = listOf(good, good, good, EndpointObservation.NONE)
        val l = RoomLabel.build(TrackingStatus.TRACKING, obs, plan)
        assertEquals(AreaContent.Hint(ConfidenceReason.NO_HIT), l.area)
        assertTrue(l.perimeter is LabelContent.Hint)
        assertTrue(l.walls[0] is LabelContent.Length) // taps 0-1
        assertTrue(l.walls[2] is LabelContent.Hint) // taps 2-3
        assertEquals(ConfidenceLevel.NONE, l.level)
    }

    @Test fun noNumbersWhenTrackingLost() {
        val l = RoomLabel.build(TrackingStatus.PAUSED, List(4) { good }, plan)
        assertTrue(l.walls.all { it is LabelContent.Hint })
        assertTrue(l.area is AreaContent.Hint)
    }

    @Test fun ceilingVolumeAndUnevenCap() {
        val withCeiling = plan.withCeiling(Vec3(0f, 2.5f, 0f))
        val l = RoomLabel.build(TrackingStatus.TRACKING, List(4) { good }, withCeiling, good)
        assertEquals(2.5, (l.ceiling as LabelContent.Length).meters, 1e-5)
        assertEquals(30.0, (l.volume as VolumeContent.Volume).cubicMeters, 1e-3)
        val missing = RoomLabel.build(TrackingStatus.TRACKING, List(4) { good }, withCeiling, null)
        assertTrue(missing.volume is VolumeContent.Hint)
        val uneven = RoomPlan.fromCorners(room.mapIndexed { i, v -> if (i == 2) v.copy(y = 0.3f) else v })
        val u = RoomLabel.build(TrackingStatus.TRACKING, List(4) { good }, uneven)
        assertEquals(ConfidenceLevel.LOW, (u.area as AreaContent.Area).level)
        assertTrue(u.area.let { it is AreaContent.Area && it.notPlanar })
    }

    @Test fun selfIntersectingAreaInvalid() {
        val bow = RoomPlan.fromCorners(listOf(room[0], room[2], room[1], room[3]))
        val l = RoomLabel.build(TrackingStatus.TRACKING, List(4) { good }, bow)
        assertEquals(AreaContent.Invalid, l.area)
    }

    @Test fun fitKeepsAspectAndCentres() {
        val s = PlanLayout.fit(plan, 1000f, 500f, 50f)
        // 4 x 3 m into 900 x 400 -> 133.3 px/m (height bound).
        assertEquals(400f / 3f, s.pxPerMeter, 1e-3f)
        val xs = s.corners.map { it.x }
        val ys = s.corners.map { it.y }
        assertEquals(500f, (xs.min() + xs.max()) / 2f, 1e-2f)
        assertEquals(250f, (ys.min() + ys.max()) / 2f, 1e-2f)
        assertEquals(50f, ys.min(), 1e-2f)
        // Plan y up -> screen y down: corner 2 (plan y = 3) is above corner 1 (plan y = 0).
        assertTrue(s.corners[2].y < s.corners[1].y)
        assertEquals(500f, s.center.x, 1e-2f)
        assertEquals(250f, s.center.y, 1e-2f)
    }

    @Test fun wallLabelsSitOutside() {
        val s = PlanLayout.fit(plan, 1000f, 1000f, 100f, labelGap = 10f, labelSizes = List(4) { LabelSize(60f, 20f) })
        val xs = s.corners.map { it.x }
        val ys = s.corners.map { it.y }
        // Wall 0 is the bottom (plan y = 0) -> label below the room; wall 1 is the right side -> label right of it.
        assertTrue(s.wallLabels[0].y > ys.max())
        assertTrue(s.wallLabels[1].x > xs.max())
        assertTrue(s.wallLabels[2].y < ys.min())
        assertTrue(s.wallLabels[3].x < xs.min())
        assertEquals(ys.max() + 10f + 10f, s.wallLabels[0].y, 1e-2f)
    }

    @Test fun fitEmptyAndSinglePoint() {
        assertTrue(PlanLayout.fit(RoomPlan.fromCorners(emptyList()), 100f, 100f, 10f).corners.isEmpty())
        val one = PlanLayout.fit(RoomPlan.fromCorners(listOf(Vec3(1f, 0f, 1f))), 100f, 100f, 10f)
        assertEquals(50f, one.corners[0].x, 1e-3f)
    }

    @Test fun svgHasPolygonDimensionsAndEscapes() {
        val svg = PlanSvg.toSvg(plan.withCeilingHeight(2.5), UnitSystem.METRIC, PlanSvgText(title = "Kitchen <1> & \"2\""))
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("<polygon points=\""))
        assertTrue(svg.contains(">4.00 m</text>"))
        assertTrue(svg.contains(">3.00 m</text>"))
        assertTrue(svg.contains(">12.00 m²</text>"))
        assertTrue(svg.contains("Perimeter 14.00 m"))
        assertTrue(svg.contains("Ceiling height 2.50 m"))
        assertTrue(svg.contains("Volume 30.000 m³"))
        assertTrue(svg.contains("Kitchen &lt;1&gt; &amp; &quot;2&quot;"))
        assertFalse(svg.contains("<1>"))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        val imp = PlanSvg.toSvg(plan, UnitSystem.IMPERIAL)
        assertTrue(imp.contains("13 ft 1.5 in"))
        assertTrue(imp.contains("ft²"))
    }

    @Test fun svgInvalidPlanHasNoArea() {
        val bow = RoomPlan.fromCorners(listOf(room[0], room[2], room[1], room[3]))
        assertTrue(PlanSvg.summaryLines(bow, UnitSystem.METRIC).isEmpty())
        assertFalse(PlanSvg.toSvg(bow, UnitSystem.METRIC).contains("m²"))
        assertEquals("a&apos;b", PlanSvg.escape("a'b\u0001"))
    }
}
