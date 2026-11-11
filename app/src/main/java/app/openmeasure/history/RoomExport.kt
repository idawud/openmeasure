package app.openmeasure.history

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import app.openmeasure.R
import app.openmeasure.measure.export.PageMetrics
import app.openmeasure.measure.export.PdfLayout
import app.openmeasure.measure.room.LabelSize
import app.openmeasure.measure.room.PlanLayout
import app.openmeasure.measure.room.PlanSvg
import app.openmeasure.measure.room.PlanSvgText
import app.openmeasure.measure.room.RoomPlan
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** F15 export of one floor plan as SVG or a one-page A4 PDF, shared through FileProvider like [ExportActions]. */
internal object RoomExport {
    const val SVG_MIME = "image/svg+xml"
    const val PDF_MIME = "application/pdf"

    fun svgText(context: Context) = PlanSvgText(
        title = context.getString(R.string.room_plan_title),
        area = context.getString(R.string.room_area_caption),
        perimeter = context.getString(R.string.room_perimeter_caption),
        ceiling = context.getString(R.string.room_ceiling_caption),
        volume = context.getString(R.string.room_volume_caption),
    )

    suspend fun svg(context: Context, plan: RoomPlan, units: UnitSystem): Uri = withContext(Dispatchers.IO) {
        val file = ExportActions.newFile(context, "svg", "openmeasure-room")
        file.writeText(PlanSvg.toSvg(plan, units, svgText(context)), Charsets.UTF_8)
        ExportActions.uriFor(context, file)
    }

    suspend fun pdf(context: Context, plan: RoomPlan, units: UnitSystem): Uri = withContext(Dispatchers.IO) {
        val file = ExportActions.newFile(context, "pdf", "openmeasure-room")
        writePdf(context, plan, units, file)
        ExportActions.uriFor(context, file)
    }

    /** Title, the plan fitted between the title block and the summary lines, then the accuracy disclaimer. */
    private fun writePdf(context: Context, plan: RoomPlan, units: UnitSystem, out: File) {
        val m = PageMetrics()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 22f }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF222222.toInt() }
        val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F0FE.toInt() }
        val lines = PlanSvg.summaryLines(plan, units, svgText(context))
        val summaryH = lines.size * m.lineHeight + m.rowPadding
        val top = m.margin + m.titleBlockHeight
        val bottom = m.height - m.margin - m.footerHeight - summaryH
        val texts = plan.wallLengths.map { LengthFormatter.format(it, units) }
        val sizes = texts.map { LabelSize(dim.measureText(it) + 6f, 14f) }
        val fit = PlanLayout.fit(plan, m.width, bottom - top, 50f, 6f, sizes)
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(m.width.toInt(), m.height.toInt(), 1).create())
            val c = page.canvas
            c.drawText(context.getString(R.string.room_plan_title), m.margin, m.margin + 22f, title)
            c.drawText(dateText(System.currentTimeMillis()), m.margin, m.margin + 42f, body)
            c.save()
            c.translate(0f, top)
            if (fit.corners.size >= 2) {
                val path = Path().apply {
                    moveTo(fit.corners[0].x, fit.corners[0].y)
                    fit.corners.drop(1).forEach { lineTo(it.x, it.y) }
                    if (fit.corners.size >= 3) close()
                }
                if (fit.corners.size >= 3) c.drawPath(path, fill)
                c.drawPath(path, wall)
            }
            texts.forEachIndexed { i, t -> fit.wallLabels.getOrNull(i)?.let { c.drawText(t, it.x, it.y + 4f, dim) } }
            if (plan.isValid) {
                val area = Paint(dim).apply { textSize = 13f }
                c.drawText(AreaFormatter.format(plan.areaM2, units), fit.center.x, fit.center.y + 5f, area)
            }
            c.restore()
            lines.forEachIndexed { i, l -> c.drawText(l, m.margin, bottom + m.rowPadding + (i + 1) * m.lineHeight, body) }
            val footer = PdfLayout.wrap(context.getString(R.string.disclaimer_body), 110, 2)
            footer.forEachIndexed { n, l -> c.drawText(l, m.margin, m.height - m.margin - 26f + n * 12f, body) }
            doc.finishPage(page)
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
