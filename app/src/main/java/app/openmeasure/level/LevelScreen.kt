package app.openmeasure.level

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.R
import app.openmeasure.measure.level.LevelMode
import app.openmeasure.measure.level.LevelReading
import app.openmeasure.settings.SettingsRepository
import java.util.Locale

private val LevelGreen = Color(0xFF2E7D32)

@Composable
fun LevelScreen(settingsRepository: SettingsRepository) {
    val sensor = rememberLevelSensor()
    val reading = sensor.reading
    val settings = settingsRepository.settings
    val view = LocalView.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 40) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    val isLevel = reading?.isLevel == true
    LaunchedEffect(isLevel) {
        if (isLevel && settings.hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    // Rising tone: a higher pitch for each whole degree closer to level (within 5 degrees).
    val approachStep = reading?.tiltDeg?.takeIf { it < 5f }?.toInt()
    LaunchedEffect(approachStep, isLevel, settings.soundEnabled) {
        if (!settings.soundEnabled || approachStep == null) return@LaunchedEffect
        val t = when {
            isLevel -> ToneGenerator.TONE_PROP_ACK
            approachStep >= 4 -> ToneGenerator.TONE_DTMF_1
            approachStep == 3 -> ToneGenerator.TONE_DTMF_4
            approachStep == 2 -> ToneGenerator.TONE_DTMF_7
            else -> ToneGenerator.TONE_DTMF_9
        }
        tone?.startTone(t, 80)
    }

    val bg = if (isLevel) LevelGreen else MaterialTheme.colorScheme.background
    val fg = if (isLevel) Color.White else MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxSize().background(bg).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!sensor.available) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.level_no_sensor), color = fg)
            }
            return@Column
        }
        val modeRes = if (reading?.mode == LevelMode.EDGE) R.string.level_mode_edge else R.string.level_mode_flat
        Text(stringResource(modeRes), color = fg, style = MaterialTheme.typography.titleMedium)
        Text(
            text = reading?.let { String.format(Locale.US, "%.1f°", it.tiltDeg) } ?: "--",
            color = fg,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (reading?.mode) {
                LevelMode.EDGE -> EdgeBar(reading, fg)
                LevelMode.FLAT -> FlatBubble(reading, fg)
                null -> Unit
            }
        }
        Text(stringResource(R.string.level_hint), color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FlatBubble(r: LevelReading, color: Color) {
    Canvas(Modifier.fillMaxSize().aspectRatio(1f)) {
        val c = center
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(color, radius, c, style = Stroke(4.dp.toPx()))
        drawLine(color, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 1.dp.toPx())
        drawLine(color, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius), 1.dp.toPx())
        drawCircle(color, radius * 0.15f, c, style = Stroke(2.dp.toPx()))
        val travel = radius * 0.85f
        val bubble = Offset(c.x + r.bubbleX * travel, c.y - r.bubbleY * travel)
        drawCircle(color.copy(alpha = 0.8f), radius * 0.12f, bubble)
    }
}

@Composable
private fun EdgeBar(r: LevelReading, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val h = 48.dp.toPx()
        val top = (size.height - h) / 2f
        drawRoundRect(
            color, Offset(0f, top), Size(size.width, h),
            cornerRadius = CornerRadius(h / 2), style = Stroke(4.dp.toPx()),
        )
        drawLine(color, Offset(size.width / 2, top), Offset(size.width / 2, top + h), 2.dp.toPx())
        val offset = (r.rollDeg / 10f).coerceIn(-1f, 1f) * (size.width / 2 - h / 2)
        drawCircle(color.copy(alpha = 0.8f), h / 2 - 6.dp.toPx(), Offset(size.width / 2 + offset, size.height / 2))
    }
}
