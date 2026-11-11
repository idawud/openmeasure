package app.openmeasure.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.GroupCard
import app.openmeasure.ui.OmColors

/** Small grey section title above a card. Marked as a heading for TalkBack navigation. */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = OmColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Rounded card holding a section's rows, separated by hairlines. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GroupCard(content = content)
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = OmColors.Outline)

/** A radio-style row for choosing one option. The whole row is the tap target. */
@Composable
fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = OmColors.Accent, unselectedColor = OmColors.TextSecondary),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A switch row with title and optional description. The whole row toggles the value. */
@Composable
fun ToggleRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = OmColors.Success, checkedThumbColor = Color.White, checkedBorderColor = OmColors.Success,
                uncheckedTrackColor = OmColors.SurfaceHigh, uncheckedThumbColor = Color.White, uncheckedBorderColor = OmColors.SurfaceHigh,
            ),
        )
    }
}
