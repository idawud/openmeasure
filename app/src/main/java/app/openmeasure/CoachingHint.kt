package app.openmeasure

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.rememberReduceMotion

/** iOS-style "Move your phone to start": sweeping phone above a dark centred pill; static when animations are off. */
@Composable
fun CoachingHint(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val description = stringResource(R.string.coaching_move_slowly_desc)
    val sweep = if (reduceMotion) 0.5f else {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sweepX",
        ).value
    }
    Column(
        modifier.semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.width(120.dp).height(72.dp)) {
            val pw = 26.dp.toPx(); val ph = 46.dp.toPx()
            val x = (size.width - pw) * sweep
            val topLeft = Offset(x, (size.height - ph) / 2)
            // Soft dark under-stroke keeps the icon visible over bright surfaces.
            rotate((sweep - 0.5f) * 24f, Offset(x + pw / 2, size.height / 2)) {
                drawRoundRect(OmColors.LineOutline, topLeft, Size(pw, ph), CornerRadius(7.dp.toPx()), Stroke(5.dp.toPx()))
                drawRoundRect(Color.White, topLeft, Size(pw, ph), CornerRadius(7.dp.toPx()), Stroke(2.5.dp.toPx()))
                drawCircle(Color.White, 2.dp.toPx(), Offset(x + pw / 2, topLeft.y + ph - 6.dp.toPx()))
            }
        }
        HintBanner(stringResource(R.string.coaching_move_slowly), Modifier.padding(top = 8.dp))
    }
}
