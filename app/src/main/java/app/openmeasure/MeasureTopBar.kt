package app.openmeasure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.PillButton

/** Top-left: "Clear" (and "Done" / "New" for a line path), visible only when something is placed. */
@Composable
fun MeasureTopBar(s: MeasureUiState, controller: MeasureController, modifier: Modifier = Modifier) {
    if (s.pointCount == 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(stringResource(R.string.measure_clear), { controller.reset() })
        if (!s.rectMode && !s.angleMode && !s.boxMode && !s.roomMode) {
            // "Done" ends the current path; afterwards the same pill reads "New" (next point starts fresh).
            PillButton(
                stringResource(if (s.finished) R.string.measure_new else R.string.measure_done),
                { if (s.finished) controller.reset() else controller.done() },
                filled = !s.finished,
            )
        }
    }
}
