package app.openmeasure

import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.rect.RectWarning
import app.openmeasure.measure.snap.SnapKind

/** One drawn line with its label; [live] = last placed point to the reticle. Numbers only inside Length. */
data class SegmentUi(
    val segment: ScreenSegment?,
    val label: LabelContent,
    val live: Boolean,
    /** F13 ruler ticks (empty unless a number is displayable and the segment is long enough on screen). */
    val ticks: List<ScreenTick> = emptyList(),
)

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
    /** Active snap (drag if dragging, else reticle); shown as shape + text, never colour alone. */
    val snapKind: SnapKind = SnapKind.NONE,
    /** Where the snapped point is on screen (view pixels), null when not snapped. */
    val snapPoint: ScreenPoint? = null,
    /** Increments on every snap acquire; the UI ticks haptics when it changes. */
    val snapTick: Int = 0,
    /** F12 guide references and alignment; null when disabled or no start point. */
    val guide: GuideUi? = null,
    /** Increments on every guide acquire; the UI ticks haptics when it changes. */
    val guideTick: Int = 0,
    /** F9: rectangle mode (max 4 corners, closed quad with area). */
    val rectMode: Boolean = false,
    /** F16: angle mode (A, vertex, B). */
    val angleMode: Boolean = false,
    /** Angle label and arc data once 3 points exist in angle mode. */
    val angle: AngleUi? = null,
    /** F14: box (volume) mode (A, B width; C depth; T height). */
    val boxMode: Boolean = false,
    /** Box step, wireframe and volume label; non-null in box mode. */
    val box: BoxUi? = null,
    /** F15: room (floor plan) mode. */
    val roomMode: Boolean = false,
    /** Room step, live preview plan and closed result; non-null in room mode. */
    val room: RoomUi? = null,
    /** Area label + warnings once 4 corners exist in rectangle mode. */
    val rect: RectUi? = null,
    /** Increments each time auto-detect finds nothing; the UI shows the manual-corners hint. */
    val detectFailTick: Int = 0,
    /** Non-null when a displayable measurement exists that the user may save to history. */
    val save: SaveCandidate? = null,
)

/** Rectangle overlay: [area] gated by confidence; [center] is the projected quad centre (null if behind camera). */
data class RectUi(val area: AreaContent, val center: ScreenPoint?, val warnings: Set<RectWarning>)
