package app.openmeasure.measure.room

import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter
import java.util.Locale

/** Localised captions for the SVG (defaults are English; the app passes string resources). */
data class PlanSvgText(
    val title: String? = null,
    val area: String = "Area",
    val perimeter: String = "Perimeter",
    val ceiling: String = "Ceiling height",
    val volume: String = "Volume",
)

/** F15 export: a standalone SVG floor plan with wall dimensions, area and summary lines. Pure string building. */
object PlanSvg {
    const val WIDTH = 800f
    const val HEIGHT = 600f
    private const val FOOTER = 90f
    private const val PADDING = 70f
    private const val FONT = 16f
    private const val CHAR_W = 0.6f * FONT // rough sans-serif advance, only used to keep labels apart

    fun toSvg(plan: RoomPlan, units: UnitSystem, text: PlanSvgText = PlanSvgText()): String {
        val wallTexts = plan.wallLengths.map { LengthFormatter.format(it, units) }
        val top = if (text.title != null) 36f else 0f
        val drawH = HEIGHT - FOOTER - top
        val fit = PlanLayout.fit(plan, WIDTH, drawH, PADDING, 10f, wallTexts.map { LabelSize(it.length * CHAR_W + 8f, FONT + 6f) })
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${n(WIDTH)}" height="${n(HEIGHT)}" viewBox="0 0 ${n(WIDTH)} ${n(HEIGHT)}">""").append('\n')
        sb.append("""<rect width="100%" height="100%" fill="#ffffff"/>""").append('\n')
        text.title?.let { sb.append("""<text x="20" y="28" font-family="sans-serif" font-size="20" font-weight="bold" fill="#1a1a1a">${escape(it)}</text>""").append('\n') }
        sb.append("""<g transform="translate(0 ${n(top)})">""").append('\n')
        if (fit.corners.size >= 2) {
            val pts = fit.corners.joinToString(" ") { "${n(it.x)},${n(it.y)}" }
            sb.append("""<polygon points="$pts" fill="#e8f0fe" stroke="#1a1a1a" stroke-width="4" stroke-linejoin="round"/>""").append('\n')
        }
        fit.corners.forEach { sb.append("""<circle cx="${n(it.x)}" cy="${n(it.y)}" r="4" fill="#1a1a1a"/>""").append('\n') }
        sb.append("""<g font-family="sans-serif" font-size="${n(FONT)}" fill="#1a1a1a" text-anchor="middle">""").append('\n')
        wallTexts.forEachIndexed { i, t ->
            val p = fit.wallLabels[i]
            sb.append("""<text x="${n(p.x)}" y="${n(p.y)}" dy="0.35em">${escape(t)}</text>""").append('\n')
        }
        if (plan.isValid) {
            sb.append("""<text x="${n(fit.center.x)}" y="${n(fit.center.y)}" dy="0.35em" font-weight="bold">${escape(AreaFormatter.format(plan.areaM2, units))}</text>""").append('\n')
        }
        sb.append("</g>\n</g>\n")
        summaryLines(plan, units, text).forEachIndexed { i, line ->
            sb.append("""<text x="20" y="${n(HEIGHT - FOOTER + 24f + i * 20f)}" font-family="sans-serif" font-size="15" fill="#1a1a1a">${escape(line)}</text>""").append('\n')
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    /** "Area 14.20 m²", "Perimeter 15.40 m", optional ceiling height and volume (only for a valid plan). */
    fun summaryLines(plan: RoomPlan, units: UnitSystem, text: PlanSvgText = PlanSvgText()): List<String> {
        if (!plan.isValid) return emptyList()
        return listOfNotNull(
            "${text.area} ${AreaFormatter.format(plan.areaM2, units)}",
            "${text.perimeter} ${LengthFormatter.format(plan.perimeterM, units)}",
            plan.ceilingHeightM?.let { "${text.ceiling} ${LengthFormatter.format(it, units)}" },
            plan.volumeM3?.let { "${text.volume} ${VolumeFormatter.format(it, units)}" },
        )
    }

    /** XML text/attribute escaping; drops control characters that are illegal in XML 1.0. */
    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c >= ' ' || c == '\t' || c == '\n' || c == '\r') append(c)
        }
    }

    private fun n(v: Float): String = String.format(Locale.ROOT, "%.1f", v)
}
