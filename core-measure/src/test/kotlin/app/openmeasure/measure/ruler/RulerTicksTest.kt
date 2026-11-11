package app.openmeasure.measure.ruler

import app.openmeasure.measure.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerTicksTest {
    @Test fun tooShortOnScreen_noTicks() {
        assertTrue(RulerTicks.generate(1.0, UnitSystem.METRIC, 30.0).isEmpty())
    }

    @Test fun invalidInputs_noTicks() {
        assertTrue(RulerTicks.generate(Double.NaN, UnitSystem.METRIC, 500.0).isEmpty())
        assertTrue(RulerTicks.generate(-1.0, UnitSystem.METRIC, 500.0).isEmpty())
        assertTrue(RulerTicks.generate(1.0, UnitSystem.METRIC, 0.0).isEmpty())
        assertTrue(RulerTicks.generate(1.0, UnitSystem.METRIC, Double.POSITIVE_INFINITY).isEmpty())
    }

    @Test fun metricCloseUp_millimetreTicks() {
        // 10 000 px/m -> 10 px per mm.
        val t = RulerTicks.generate(0.05, UnitSystem.METRIC, 10_000.0)
        assertEquals(50, t.size)
        assertEquals(0.001, t.first().positionMeters, 1e-9)
        assertEquals(TickKind.MEDIUM, t[4].kind) // 5 mm
        assertEquals(TickKind.MINOR, t[0].kind)
    }

    @Test fun metricMidRange_tenCentimetreMajorsLabelled() {
        // 400 px/m: 1 cm = 4 px (too tight), 5 cm = 20 px.
        val t = RulerTicks.generate(1.0, UnitSystem.METRIC, 400.0)
        assertEquals(0.05, t.first().positionMeters, 1e-9)
        val tenCm = t.first { it.positionMeters > 0.099 }
        assertEquals(TickKind.MAJOR, tenCm.kind)
        assertEquals("10 cm", tenCm.label)
        assertEquals(TickKind.MINOR, t.first().kind)
        assertTrue(t.zipWithNext().all { (a, b) -> (b.positionMeters - a.positionMeters) * 400.0 >= 8.0 })
    }

    @Test fun metricZoomedOut_metreMajors() {
        // 10 px/m, 5 m -> 50 px; only 1 m (10 px) fits, too tight for text.
        val t = RulerTicks.generate(5.0, UnitSystem.METRIC, 10.0)
        assertEquals(5, t.size)
        assertTrue(t.all { it.kind == TickKind.MAJOR && it.label == null })
        // 40 px/m: half-metre ticks, metre labels.
        val u = RulerTicks.generate(3.0, UnitSystem.METRIC, 40.0)
        assertEquals(6, u.size)
        assertEquals(listOf("1 m", "2 m", "3 m"), u.mapNotNull { it.label })
    }

    @Test fun metricTooFarForAnyTick() {
        assertTrue(RulerTicks.generate(20.0, UnitSystem.METRIC, 4.0).isEmpty()) // 80 px long, 4 px per metre
    }

    @Test fun imperialCloseUp_sixteenths() {
        val ppm = 200.0 / 0.0254 // 200 px per inch
        val t = RulerTicks.generate(0.0254 * 2, UnitSystem.IMPERIAL, ppm)
        assertEquals(32, t.size)
        assertEquals(0.0254 / 16, t.first().positionMeters, 1e-9)
        assertEquals(TickKind.MEDIUM, t[7].kind) // 1/2 in
        assertEquals(TickKind.MAJOR, t[15].kind)
        assertEquals("1 in", t[15].label)
        assertEquals("2 in", t[31].label)
    }

    @Test fun imperialZoomedOut_feetLabelled() {
        val ppm = 3.0 / 0.0254 // 3 px per inch -> 36 px per foot
        val t = RulerTicks.generate(0.0254 * 36, UnitSystem.IMPERIAL, ppm)
        assertEquals(TickKind.MINOR, t.first().kind) // 6 in
        val ft = t.filter { it.label != null }
        assertEquals(listOf("1 ft", "2 ft", "3 ft"), ft.map { it.label })
    }

    @Test fun positionsStayWithinLength() {
        val t = RulerTicks.generate(0.337, UnitSystem.METRIC, 3000.0)
        assertTrue(t.all { it.positionMeters <= 0.337 + 1e-9 })
    }
}
