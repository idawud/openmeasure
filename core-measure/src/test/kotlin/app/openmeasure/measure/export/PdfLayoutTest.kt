package app.openmeasure.measure.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {
    private val m = PageMetrics()
    private fun row(note: String? = null, thumb: Boolean = false) = PdfRowInput("t", "d", note, thumb)

    @Test fun emptyGivesOnePage() {
        val p = PdfLayout.paginate(emptyList(), m)
        assertEquals(1, p.size)
        assertTrue(p[0].rows.isEmpty())
    }

    @Test fun firstPageStartsBelowTitle() {
        val p = PdfLayout.paginate(listOf(row()), m)
        assertEquals(m.margin + m.titleBlockHeight, p[0].rows[0].top, 0.01f)
        assertTrue(p[0].isFirst)
    }

    @Test fun paginatesWithoutOverflowAndKeepsOrder() {
        val rows = List(60) { row("note", thumb = true) }
        val pages = PdfLayout.paginate(rows, m)
        assertTrue(pages.size > 1)
        assertEquals((0 until 60).toList(), pages.flatMap { p -> p.rows.map { it.index } })
        for (p in pages) for (r in p.rows) assertTrue(r.top + r.height <= m.height - m.margin - m.footerHeight + 0.01f)
        assertEquals(m.margin, pages[1].rows[0].top, 0.01f)
        assertEquals(pages.indices.map { it + 1 }, pages.map { it.number })
        assertTrue(!pages[1].isFirst)
    }

    @Test fun thumbRaisesRowHeight() {
        val a = PdfLayout.paginate(listOf(row()), m)[0].rows[0].height
        val b = PdfLayout.paginate(listOf(row(thumb = true)), m)[0].rows[0].height
        assertTrue(b > a)
    }

    @Test fun wrapAndTruncate() {
        assertEquals(listOf("aaa bbb", "ccc"), PdfLayout.wrap("aaa bbb ccc", 7, 3))
        val t = PdfLayout.wrap("a\nb\nc\nd", 10, 2)
        assertEquals(2, t.size)
        assertTrue(t[1].endsWith("…"))
        assertEquals(listOf("abcd", "efgh"), PdfLayout.wrap("abcdefgh", 4, 3))
        assertTrue(PdfLayout.wrap(null, 10, 3).isEmpty())
    }
}
