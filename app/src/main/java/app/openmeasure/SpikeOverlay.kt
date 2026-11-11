package app.openmeasure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.hit.HitSource
import com.google.ar.core.TrackingState

@Composable
fun SpikeOverlay(controller: SpikeController) {
    val s by controller.state.collectAsState()
    val ok = s.tracking == TrackingState.TRACKING && s.source != HitSource.NONE
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { controller.tap(it.x, it.y) }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val col = if (ok) Color.Green else Color.Red
            drawCircle(col, radius = 18f, center = c, style = Stroke(3f))
            drawCircle(col, radius = 3f, center = c)
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0x99000000)).padding(8.dp),
        ) {
            Text("State: ${s.tracking}", color = Color.White)
            Text("Hit: ${s.source}  conf=${"%.2f".format(s.confidence)}", color = Color.White)
            Text("A=${if (s.pointA != null) "set" else "-"}  B=${if (s.pointB != null) "set" else "-"}", color = Color.White)
            val d = s.distanceCm
            if (d != null && s.tracking == TrackingState.TRACKING) {
                Text(
                    "Distance: ${"%.1f".format(d)} cm (${s.distanceSource}, conf ${"%.2f".format(s.distanceConfidence)})",
                    color = Color.Yellow,
                )
            } else if (!ok) {
                Text("Tracking poor / no hit", color = Color.Red)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
            Button(onClick = { controller.tap() }) { Text("Place point") }
            Button(onClick = { controller.reset() }) { Text("Reset") }
        }
    }
}
