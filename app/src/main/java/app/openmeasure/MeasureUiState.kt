package app.openmeasure

import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenSegment

/** One drawn line with its label; [live] = last placed point to the reticle. Numbers only inside Length. */
data class SegmentUi(val segment: ScreenSegment?, val label: LabelContent, val live: Boolean)

/** Per-frame UI snapshot of the measurement path (screen-space, view pixels). */
data class MeasureUiState(
    val coaching: CoachingState = CoachingState.MOVE_SLOWLY,
    /** Placed points projected to the screen; null entries are behind the camera. */
    val dots: List<ScreenPoint?> = emptyList(),
    /** Ids parallel to [dots], for touch picking. */
    val ids: List<PointId> = emptyList(),
    /** Placed segments, plus a trailing live one while a path is being drawn. */
    val segments: List<SegmentUi> = emptyList(),
    /** Total metres; null unless >= 2 placed segments and all displayable. */
    val totalMeters: Double? = null,
    val pointCount: Int = 0,
    /** Path was ended with Done; the next placed point starts a new path. */
    val finished: Boolean = false,
    val canAdd: Boolean = true,
    val canUndo: Boolean = false,
    /** Point currently being dragged, if any. */
    val draggingId: PointId? = null,
)
