package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.ui.ConfidenceDot
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmShapes
import app.openmeasure.ui.OmText

fun confidenceColor(level: ConfidenceLevel): Color = when (level) {
    ConfidenceLevel.HIGH -> OmColors.Success
    ConfidenceLevel.MEDIUM -> OmColors.Warning
    else -> OmColors.Danger
}

@Composable
fun confidenceText(level: ConfidenceLevel): String = stringResource(
    when (level) {
        ConfidenceLevel.HIGH -> R.string.confidence_high
        ConfidenceLevel.MEDIUM -> R.string.confidence_medium
        else -> R.string.confidence_low
    },
)

/**
 * The iOS-style measurement label: dark (70% black) rounded pill with the value in white bold tabular digits and the
 * confidence as a small coloured dot plus word (text + colour, never colour alone). Tapping opens the detail sheet when [onClick] is set.
 */
@Composable
fun LabelPill(
    main: String,
    level: ConfidenceLevel?,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    mainStyle: TextStyle = OmText.measure,
    secondary: String? = null,
    notes: List<String> = emptyList(),
) {
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.detail_open), role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier
            .clip(OmShapes.label)
            .background(OmColors.OverlayLabel)
            .then(tap)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(main, style = mainStyle, color = Color.White)
        secondary?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White) }
        if (level != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ConfidenceDot(confidenceColor(level))
                Text(confidenceText(level), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = OmColors.Accent) }
    }
}

/** Label shown instead of a number when confidence says no value may be displayed (hint text only). */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(OmShapes.label)
            .background(OmColors.OverlayLabel)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ConfidenceDot(OmColors.Danger)
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}
