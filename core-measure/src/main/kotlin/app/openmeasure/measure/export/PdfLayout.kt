package app.openmeasure.measure.export

/** Page geometry in points (A4 = 595 x 842). */
data class PageMetrics(
    val width: Float = 595f,
    val height: Float = 842f,
    val margin: Float = 40f,
    val titleBlockHeight: Float = 70f,
    val footerHeight: Float = 40f,
    val lineHeight: Float = 15f,
    val rowPadding: Float = 10f,
    val thumbSize: Float = 56f,
    val noteCharsPerLine: Int = 70,
    val maxNoteLines: Int = 3,
)

/** Pre-formatted (localised) text for one row; the layout only decides positions. */
data class PdfRowInput(val title: String, val detail: String, val note: String?, val hasThumb: Boolean)

/** [top] is the y of the row's top edge; [noteLines] are already wrapped/truncated. */
data class PlacedRow(val index: Int, val top: Float, val height: Float, val noteLines: List<String>, val hasThumb: Boolean)

data class PdfPage(val number: Int, val rows: List<PlacedRow>, val isFirst: Boolean)

object PdfLayout {
    fun paginate(rows: List<PdfRowInput>, m: PageMetrics = PageMetrics()): List<PdfPage> {
        val pages = mutableListOf<PdfPage>()
        val limit = m.height - m.margin - m.footerHeight
        var current = mutableListOf<PlacedRow>()
        var y = m.margin + m.titleBlockHeight
        fun flush() {
            pages += PdfPage(pages.size + 1, current, pages.isEmpty())
            current = mutableListOf()
            y = m.margin
        }
        rows.forEachIndexed { i, r ->
            val noteLines = wrap(r.note, m.noteCharsPerLine, m.maxNoteLines)
            val textH = (2 + noteLines.size) * m.lineHeight
            val h = maxOf(textH, if (r.hasThumb) m.thumbSize else 0f) + m.rowPadding
            if (y + h > limit && current.isNotEmpty()) flush()
            current += PlacedRow(i, y, h, noteLines, r.hasThumb)
            y += h
        }
        flush()
        return pages
    }

    /** Greedy word wrap; hard-splits long words; truncates with an ellipsis past [maxLines]. */
    fun wrap(text: String?, width: Int, maxLines: Int): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<String>()
        for (para in text.trim().split('\n')) {
            var line = ""
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap { it.chunked(width) }
            for (word in words) {
                line = when {
                    line.isEmpty() -> word
                    line.length + 1 + word.length <= width -> "$line $word"
                    else -> { lines += line; word }
                }
            }
            if (line.isNotEmpty()) lines += line
        }
        if (lines.size <= maxLines) return lines
        return lines.take(maxLines).mapIndexed { i, l -> if (i == maxLines - 1) l.take(width - 1).trimEnd() + "…" else l }
    }
}
