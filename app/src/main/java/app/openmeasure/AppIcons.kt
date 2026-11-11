package app.openmeasure

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons drawn locally so the app needs no material-icons dependency.
 * Tint comes from the Icon composable (LocalContentColor), so fill colour here is a placeholder.
 */
object AppIcons {
    /** Ruler outline: Measure tab. */
    val Measure: ImageVector by lazy { icon("Measure", "M3,8h18v8h-18z M5,10v4h2v-4z M9,10v4h2v-4z M13,10v4h2v-4z M17,10v4h2v-4z") }

    /** Horizontal bar with centre bubble: Level tab. */
    val Level: ImageVector by lazy { icon("Level", "M2,9h20v6h-20z M4,11v2h16v-2z M10,11h4v2h-4z") }

    /** Vertical ruler: Height tab. */
    val Height: ImageVector by lazy { icon("Height", "M8,3h8v18h-8z M10,5h4v2h-4z M10,9h4v2h-4z M10,13h4v2h-4z") }

    /** Settings gear (Material outline style). */
    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94l-0.36,-2.54c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41l-0.36,2.54c-0.59,0.24 -1.13,0.56 -1.62,0.94l-2.39,-0.96c-0.22,-0.08 -0.47,0 -0.59,0.22l-1.92,3.32c-0.12,0.22 -0.07,0.47 0.12,0.61l2.03,1.58c-0.05,0.3 -0.09,0.63 -0.09,0.94s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61l-2.01,-1.58zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6 -1.62,3.6 -3.6,3.6z",
        )
    }

    /** Back chevron (iOS style). */
    val ChevronBack: ImageVector by lazy { icon("ChevronBack", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z") }

    /** Plus sign for the place-point button. */
    val Plus: ImageVector by lazy { icon("Plus", "M11,5h2v6h6v2h-6v6h-2v-6H5v-2h6z") }

    /** Curved undo arrow. */
    val Undo: ImageVector by lazy {
        icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }

    /** Diagonal bar: Line mode. */
    val Line: ImageVector by lazy { icon("Line", "M3.5,18.1L18.1,3.5l2.4,2.4L5.9,20.5z") }

    /** Wedge outline: Angle mode. */
    val Angle: ImageVector by lazy { icon("Angle", "M3,20L3,4L20,20z M5.5,17.5L5.5,10.2L12.7,17.5z") }

    /** Clock with counter-clockwise arrow: History. */
    val History: ImageVector by lazy {
        icon(
            "History",
            "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
        )
    }

    val Delete: ImageVector by lazy {
        icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    }

    val MoreVert: ImageVector by lazy {
        icon(
            "MoreVert",
            "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }

    /** Rectangle outline: Rectangle history entries. */
    val Rectangle: ImageVector by lazy { icon("Rectangle", "M4,6h16v12h-16z M6,8v8h12v-8z") }

    /** Cube outline: Box (volume) history entries. */
    val Box: ImageVector by lazy {
        icon("Box", "M12,2l9,5v10l-9,5l-9,-5v-10z M12,4.3l6.9,3.7l-6.9,3.7l-6.9,-3.7z M5,9.7l6,3.3v6.6l-6,-3.3z M13,13l6,-3.3v6.6l-6,3.3z")
    }

    /** Floor plan outline with an inner wall: Room history entries (F15). */
    val Room: ImageVector by lazy { icon("Room", "M3,3h18v18h-18z M5,5h14v14h-14z M11,5h2v8h-2z M13,11h4v2h-4z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ).build()
}
