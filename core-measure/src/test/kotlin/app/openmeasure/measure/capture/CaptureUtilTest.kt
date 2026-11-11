package app.openmeasure.measure.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureUtilTest {
    @Test fun fileNameIsZeroPadded() {
        assertEquals("OpenMeasure_20260305_090702.jpg", CaptureUtil.fileName(2026, 3, 5, 9, 7, 2))
    }

    @Test fun flipRowsReversesRows() {
        val out = CaptureUtil.flipRows(intArrayOf(1, 2, 3, 4, 5, 6), 2, 3)
        assertArrayEquals(intArrayOf(5, 6, 3, 4, 1, 2), out)
    }

    @Test fun flipTwiceIsIdentity() {
        val p = IntArray(12) { it }
        assertArrayEquals(p, CaptureUtil.flipRows(CaptureUtil.flipRows(p, 4, 3), 4, 3))
    }

    @Test(expected = IllegalArgumentException::class) fun badSizeThrows() {
        CaptureUtil.flipRows(IntArray(5), 2, 3)
    }
}
