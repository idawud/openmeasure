package app.openmeasure.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.measure.export.PageMetrics
import app.openmeasure.measure.export.PdfLayout
import app.openmeasure.measure.export.PdfRowInput
import app.openmeasure.measure.units.UnitSystem
import java.io.File

/** Renders history to an A4 PDF using the framework [PdfDocument]. Call off the main thread. */
internal object PdfExporter {
    fun write(context: Context, items: List<HistoryItem>, units: UnitSystem, out: File) {
        val m = PageMetrics()
        val thumbs: List<Bitmap?> = items.map { it.photoUri?.let { u -> loadThumb(context, Uri.parse(u), 160) } }
        val inputs = items.mapIndexed { i, item ->
            PdfRowInput(
                title = listOfNotNull(valueText(item, units), boxDimsText(item, units), roomDimsText(context, item, units), context.getString(kindRes(item.kind)))
                    .joinToString(" · "),
                detail = "${context.getString(R.string.history_confidence, context.getString(confidenceRes(item.confidenceLevel)))} · ${dateText(item.createdAtMillis)}",
                note = item.note,
                hasThumb = thumbs[i] != null,
            )
        }
        val pages = PdfLayout.paginate(inputs, m)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 12f }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF444444.toInt() }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 22f }
        val doc = PdfDocument()
        try {
            for (p in pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(m.width.toInt(), m.height.toInt(), p.number).create())
                val c = page.canvas
                if (p.isFirst) {
                    c.drawText(context.getString(R.string.export_pdf_title), m.margin, m.margin + 22f, title)
                    c.drawText(dateText(System.currentTimeMillis()), m.margin, m.margin + 42f, body)
                }
                for (r in p.rows) {
                    val textX = m.margin + if (r.hasThumb) m.thumbSize + 10f else 0f
                    thumbs[r.index]?.let { drawThumb(c, it, m.margin, r.top, m.thumbSize) }
                    c.drawText(inputs[r.index].title, textX, r.top + 11f, bold)
                    c.drawText(inputs[r.index].detail, textX, r.top + 11f + m.lineHeight, body)
                    r.noteLines.forEachIndexed { n, line -> c.drawText(line, textX, r.top + 11f + m.lineHeight * (n + 2), body) }
                }
                val footer = PdfLayout.wrap(context.getString(R.string.disclaimer_body), 110, 2)
                footer.forEachIndexed { n, l -> c.drawText(l, m.margin, m.height - m.margin - 14f + n * 12f - 12f, body) }
                c.drawText("${p.number} / ${pages.size}", m.width - m.margin - 40f, m.height - m.margin + 4f, body)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
            thumbs.forEach { it?.recycle() }
        }
    }

    private fun drawThumb(c: Canvas, b: Bitmap, x: Float, y: Float, size: Float) {
        val side = minOf(b.width, b.height)
        val src = Rect((b.width - side) / 2, (b.height - side) / 2, (b.width + side) / 2, (b.height + side) / 2)
        c.drawBitmap(b, src, android.graphics.RectF(x, y, x + size, y + size), null)
    }
}

internal fun kindRes(kind: app.openmeasure.data.MeasurementKind) = when (kind) {
    app.openmeasure.data.MeasurementKind.LINE_PATH -> R.string.history_kind_line
    app.openmeasure.data.MeasurementKind.RECTANGLE -> R.string.history_kind_rect
    app.openmeasure.data.MeasurementKind.HEIGHT -> R.string.history_kind_height
    app.openmeasure.data.MeasurementKind.BOX -> R.string.history_kind_box
    app.openmeasure.data.MeasurementKind.ROOM -> R.string.history_kind_room
}

internal fun confidenceRes(level: String) = when (level) {
    "HIGH" -> R.string.confidence_high
    "MEDIUM" -> R.string.confidence_medium
    else -> R.string.confidence_low
}
