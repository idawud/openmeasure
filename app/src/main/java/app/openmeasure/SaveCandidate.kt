package app.openmeasure

import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import app.openmeasure.data.WorldPoint
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.model.Point
import app.openmeasure.measure.rect.AreaContent

/** A measurement the user may save: only built when a number is allowed to be displayed. */
data class SaveCandidate(
    val kind: MeasurementKind,
    val totalMeters: Double?,
    val areaM2: Double?,
    val level: ConfidenceLevel,
    val points: List<WorldPoint>,
    val volumeM3: Double? = null,
) {
    fun toItem(photoUri: String?, nowMillis: Long) = HistoryItem(
        createdAtMillis = nowMillis, kind = kind, totalLengthMeters = totalMeters, areaSquareMeters = areaM2,
        confidenceLevel = level.name, points = points, photoUri = photoUri, volumeCubicMeters = volumeM3,
    )

    companion object {
        /**
         * Finished line path whose every segment shows a number, a rectangle with a displayable area,
         * or a finished box (4 points, [box] non-null in box mode) with a displayable volume.
         */
        fun of(
            rectMode: Boolean, finished: Boolean, placed: List<LabelContent>, rect: RectUi?, pts: List<Point>, box: BoxUi? = null,
        ): SaveCandidate? {
            val world = pts.map { WorldPoint(it.position.x, it.position.y, it.position.z) }
            if (box != null) {
                val v = box.volume as? VolumeContent.Volume ?: return null
                if (box.step != BoxStep.DONE) return null
                return SaveCandidate(MeasurementKind.BOX, null, null, v.level, world.take(BoxModeController.POINTS), v.cubicMeters)
            }
            if (rectMode) {
                val a = rect?.area as? AreaContent.Area ?: return null
                return SaveCandidate(MeasurementKind.RECTANGLE, null, a.squareMeters, a.level, world)
            }
            if (!finished || placed.isEmpty()) return null
            val lengths = placed.map { it as? LabelContent.Length ?: return null }
            val worst = lengths.maxBy { it.level.ordinal }.level
            return SaveCandidate(MeasurementKind.LINE_PATH, lengths.sumOf { it.meters }, null, worst, world)
        }
    }
}
