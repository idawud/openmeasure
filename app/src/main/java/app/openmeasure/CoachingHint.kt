package app.openmeasure

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** "Move your phone slowly" message with a small sweeping phone icon; static when animations are off. */
@Composable
fun CoachingHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val message = stringResource(R.string.coaching_move_slowly)
    val description = stringResource(R.string.coaching_move_slowly_desc)
    val sweep = if (reduceMotion) 0.5f else {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "sweepX",
        ).value
    }
    Column(
        modifier.background(Color(0xCC000000)).padding(16.dp).semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.width(96.dp).height(48.dp)) {
            val pw = 18.dp.toPx(); val ph = 32.dp.toPx()
            val x = (size.width - pw) * sweep
            drawRoundRect(Color.White, Offset(x, (size.height - ph) / 2), Size(pw, ph), CornerRadius(4.dp.toPx()), Stroke(2.dp.toPx()))
        }
        Text(message, color = Color.White)
    }
}
