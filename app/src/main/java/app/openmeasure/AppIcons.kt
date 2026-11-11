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

    /** Back arrow (already mirrored for RTL by the caller if needed). */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20,11h-12.17l5.59,-5.59l-1.42,-1.41l-8,8l8,8l1.41,-1.41l-5.58,-5.59h12.17z")
    }

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
