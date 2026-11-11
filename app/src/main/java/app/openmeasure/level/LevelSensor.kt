package app.openmeasure.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openmeasure.measure.level.LevelMath
import app.openmeasure.measure.level.LevelMode
import app.openmeasure.measure.level.LevelReading
import app.openmeasure.measure.level.LowPassFilter
import app.openmeasure.measure.level.UpVector

/** Reads orientation sensors and publishes a filtered [LevelReading] as Compose state. */
class LevelSensor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val filter = LowPassFilter(0.15f)
    private val matrix = FloatArray(9)

    val available: Boolean get() = sensor != null
    var reading: LevelReading? by mutableStateOf(null)
        private set

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
        filter.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val up = if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            UpVector(matrix[6], matrix[7], matrix[8]) // world up in device axes
        } else {
            UpVector(event.values[0], event.values[1], event.values[2]) // gravity/accel reads +g upward
        }
        val smoothed = filter.filter(up.normalized())
        reading = LevelMath.compute(smoothed, reading?.mode ?: LevelMode.FLAT)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Creates a [LevelSensor] that is registered only while the lifecycle is RESUMED. */
@Composable
fun rememberLevelSensor(): LevelSensor {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val sensor = remember { LevelSensor(context) }
    DisposableEffect(owner, sensor) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor.start()
                Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) sensor.start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            sensor.stop()
        }
    }
    return sensor
}
