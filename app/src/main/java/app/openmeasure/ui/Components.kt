package app.openmeasure.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space reserved at the bottom by the main tab row; overlays add it to their bottom padding. */
val LocalTabBarHeight = compositionLocalOf { 0.dp }

/** True when the user turned animations off (system animator scale 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Translucent circular icon button; the 44dp visual sits in a 48dp touch target. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Dimens.iconButton,
    background: Color = OmColors.OverlayScrim,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
    }
}

/** Dark text pill button (or yellow when [filled]); 36dp visual in a 48dp touch target. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    description: String? = null,
    container: Color = OmColors.OverlayLabel,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .height(Dimens.pillHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(OmShapes.pill)
            .background(if (filled) OmColors.Accent else container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1, style = MaterialTheme.typography.labelLarge,
            color = if (filled) OmColors.Black else Color.White,
        )
    }
}

/** Small dark text pill (snap / guide chips), optionally with a leading coloured dot; announced politely. */
@Composable
fun SmallPill(text: String, modifier: Modifier = Modifier, dot: Color? = null) {
    Row(
        modifier
            .clip(OmShapes.pill)
            .background(OmColors.OverlayLabel)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) ConfidenceDot(dot)
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Centred instruction / status banner over the camera (live region, so TalkBack reads each change). */
@Composable
fun HintBanner(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(OmShapes.banner)
            .background(OmColors.OverlayLabel)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
    )
}

/** Small coloured dot; never the only carrier of meaning (always next to text). */
@Composable
fun ConfidenceDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Snackbar host in the dark app style (yellow action). */
@Composable
fun OmSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            containerColor = OmColors.SurfaceHigh,
            contentColor = Color.White,
            actionColor = OmColors.Accent,
            shape = OmShapes.card,
        )
    }
}
