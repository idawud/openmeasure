package app.openmeasure.measure.projection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointPickerTest {
    private val pts = listOf(ScreenPoint(100f, 100f), null, ScreenPoint(130f, 100f))

    @Test fun emptyReturnsNull() = assertNull(PointPicker.pick(emptyList(), 0f, 0f, 50f))

    @Test fun outsideRadiusReturnsNull() = assertNull(PointPicker.pick(pts, 100f, 200f, 32f))

    @Test fun picksWithinRadius() = assertEquals(0, PointPicker.pick(pts, 90f, 95f, 32f))

    @Test fun picksNearestWhenBothInRadius() = assertEquals(2, PointPicker.pick(pts, 120f, 100f, 32f))

    @Test fun exactRadiusIsInclusive() = assertEquals(0, PointPicker.pick(pts, 100f, 132f, 32f))

    @Test fun skipsNullEntries() = assertEquals(1, PointPicker.pick(listOf(null, ScreenPoint(5f, 5f)), 5f, 5f, 1f))

    @Test fun exactTieGoesToLowestIndex() = assertEquals(0, PointPicker.pick(pts, 115f, 100f, 32f))
}
