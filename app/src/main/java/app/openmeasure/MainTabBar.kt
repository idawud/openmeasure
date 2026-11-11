package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmShapes

/** Bottom tabs. [HEIGHT] is enabled only once the shared AR session reports depth support. */
enum class MainTab(val labelRes: Int) {
    MEASURE(R.string.tab_measure),
    LEVEL(R.string.tab_level),
    HEIGHT(R.string.tab_height);

    fun enabled(depthSupported: Boolean) = this != HEIGHT || depthSupported
}

/**
 * iOS-style tab row: plain text labels, the selected one in yellow on a faint yellow pill. Compact windows get a full-width
 * row over a bottom scrim; wide windows (>= 600dp) a floating pill at the bottom centre. Height is dimmed (no caption) without depth.
 */
@Composable
fun MainTabBar(selected: MainTab, depthSupported: Boolean, wide: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            MainTab.entries.forEach { tab -> TabItem(tab, tab == selected, tab.enabled(depthSupported)) { onSelect(tab) } }
        }
    }
    if (wide) {
        Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 12.dp)) {
            Box(Modifier.clip(OmShapes.pill).background(OmColors.OverlayLabel).padding(4.dp)) { tabs() }
        }
    } else {
        Box(
            modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, OmColors.OverlayStrong)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(top = 20.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) { tabs() }
    }
}

@Composable
private fun TabItem(tab: MainTab, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Dimens.touch)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(vertical = 6.dp)
            .clip(OmShapes.pill)
            .background(if (selected) OmColors.Accent.copy(alpha = 0.22f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) OmColors.Accent else Color.White,
        )
    }
}
