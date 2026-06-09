package org.stuhi.glyphpomodoro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Distinguishes two gestures from the accelerometer:
 *  - a quick shake above [threshold] → [onShake] (fired once motion settles): start/pause/resume.
 *  - a sustained shake above [resetThreshold] held for [resetHoldMs] → [onLongShake]: reset.
 *
 * The single shake is deferred until motion settles so a reset doesn't toggle first.
 * Reports the live magnitude via [onAccel] for the calibration UI.
 */
class ShakeDetector(
    context: Context,
    @Volatile var threshold: Float,
    @Volatile var resetThreshold: Float = threshold,
    @Volatile var resetHoldMs: Long = 1500L,
    private val onAccel: (Float) -> Unit = {},
    private val onShake: () -> Unit,
    private val onLongShake: () -> Unit = {},
) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val usesGravity = accel?.type == Sensor.TYPE_ACCELEROMETER

    private var inGesture = false
    private var lastAbove = 0L
    private var longFired = false

    private var resetStart = 0L
    private var lastResetAbove = 0L

    private var lastLog = 0L

    fun start() {
        if (accel == null) Log.w(TAG, "no accelerometer available")
        else Log.d(TAG, "start: sensor=${accel.name} threshold=$threshold reset=$resetThreshold/${resetHoldMs}ms")
        accel?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        Log.d(TAG, "stop")
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        var mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (usesGravity) mag = abs(mag - SensorManager.GRAVITY_EARTH)
        onAccel(mag)
        val now = SystemClock.elapsedRealtime()
        if (now - lastLog > 1000) {
            lastLog = now
            Log.d(TAG, "alive mag=%.1f (thr %.1f)".format(mag, threshold))
        }

        // Reset tracker: sustained motion above resetThreshold for resetHoldMs.
        if (mag > resetThreshold) {
            if (resetStart == 0L) resetStart = now
            lastResetAbove = now
            if (!longFired && now - resetStart >= resetHoldMs) {
                longFired = true
                Log.d(TAG, "LONG SHAKE -> reset")
                onLongShake()
            }
        } else if (resetStart != 0L && now - lastResetAbove >= SETTLE_MS) {
            resetStart = 0L
        }

        // Toggle gesture: a quick shake above threshold, fired once motion settles.
        if (mag > threshold) {
            lastAbove = now
            inGesture = true
        } else if (inGesture && now - lastAbove >= SETTLE_MS) {
            inGesture = false
            if (!longFired) {
                Log.d(TAG, "SHAKE (toggle)")
                onShake()
            }
            longFired = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val TAG = "GlyphShake"
        const val SETTLE_MS = 350L
    }
}
