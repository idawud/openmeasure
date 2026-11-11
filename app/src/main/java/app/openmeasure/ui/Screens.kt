package app.openmeasure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.AppIcons
import app.openmeasure.R

/**
 * Full-screen dark page: chevron back (and optional [actions]) on top, large title below, content
 * limited to [Dimens.maxContent] on wide screens. Handles safe-drawing insets itself.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(OmColors.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.widthIn(max = Dimens.maxContent).fillMaxWidth().fillMaxHeight().align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(AppIcons.ChevronBack, stringResource(R.string.back), onBack, background = OmColors.SurfaceHigh)
                Box(Modifier.weight(1f))
                actions()
            }
            Text(
                title,
                Modifier.padding(horizontal = Dimens.margin, vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, color = Color.White,
            )
            content()
        }
        Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
    }
}

/** Rounded 16dp list card (iOS grouped list). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(OmShapes.card).background(OmColors.Surface), content = content)
}
