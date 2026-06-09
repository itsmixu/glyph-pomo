package org.stuhi.glyphpomodoro

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * The Glyph Toy. The system binds this on flip-to-glyph. It is self-contained: it owns the
 * shake sensor and schedules the phase-end alarm directly — no foreground service, since a
 * service bound from the background can't legally start one on Android 14+.
 *
 * On bind: if idle, arm (blank, listen for the first shake within a 10 s window); if a session
 * is already running, just keep listening so a shake can pause/resume. Shake = start, then
 * pause/resume. The frame is always derived from [TimerController].
 */
class PomodoroToyService : Service() {

    private var gm: GlyphMatrixManager? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var shake: ShakeDetector? = null
    private var armTimeout: Runnable? = null
    @Volatile private var shakeThreshold = Settings().shakeThreshold
    @Volatile private var resetThreshold = Settings().resetShakeThreshold
    @Volatile private var resetHoldMs = Settings().resetHoldMs.toLong()
    @Volatile private var brightnessActive = Settings().brightness
    @Volatile private var brightnessPaused = Settings().pausedBrightness

    // Face-down detection (matrix is on the back, so it's only visible when screen is down).
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val orientationSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    @Volatile private var faceDown = true
    private val orientationListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { faceDown = e.values[2] < FACE_DOWN_Z }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val renderLoop = object : Runnable {
        override fun run() {
            main.postDelayed(this, renderFrame())
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerController.restore(applicationContext)
        val repo = SettingsRepo(applicationContext)
        scope.launch {
            repo.flow.collectLatest {
                shakeThreshold = it.shakeThreshold
                resetThreshold = it.resetShakeThreshold
                resetHoldMs = it.resetHoldMs.toLong()
                shake?.threshold = it.shakeThreshold
                shake?.resetThreshold = it.resetShakeThreshold
                shake?.resetHoldMs = it.resetHoldMs.toLong()
                brightnessActive = it.brightness
                brightnessPaused = it.pausedBrightness
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: toy activated (flip-to-glyph)")
        initGlyph()
        startShake()
        orientationSensor?.let {
            sensorManager.registerListener(orientationListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (TimerController.state.value.state == RunState.DORMANT) {
            TimerController.setArmed(applicationContext)
            scheduleArmTimeout()
        }
        main.post(renderLoop)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: toy deactivated")
        main.removeCallbacks(renderLoop)
        armTimeout?.let { main.removeCallbacks(it) }
        stopShake()
        sensorManager.unregisterListener(orientationListener)
        runCatching { gm?.turnOff() }
        runCatching { gm?.unInit() }
        gm = null
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        stopShake()
        super.onDestroy()
    }

    // --- shake control ---

    private fun startShake() {
        if (shake != null) return
        shake = ShakeDetector(
            context = applicationContext,
            threshold = shakeThreshold,
            resetThreshold = resetThreshold,
            resetHoldMs = resetHoldMs,
            onShake = { onShakeTrigger() },
            onLongShake = { onLongReset() },
        ).also { it.start() }
    }

    private fun stopShake() {
        shake?.stop(); shake = null
    }

    private fun onShakeTrigger() = main.post {
        Log.d(TAG, "shake trigger, state=${TimerController.state.value.state}")
        armTimeout?.let { main.removeCallbacks(it) }
        TimerController.onTrigger(applicationContext)
        PomodoroAlarm.scheduleOrCancel(applicationContext)
        PomodoroNotification.update(applicationContext)
    }

    private fun onLongReset() = main.post {
        Log.d(TAG, "long shake -> reset")
        armTimeout?.let { main.removeCallbacks(it) }
        TimerController.reset(applicationContext)
        PomodoroAlarm.cancel(applicationContext)
        PomodoroNotification.update(applicationContext)
    }

    private fun scheduleArmTimeout() {
        armTimeout?.let { main.removeCallbacks(it) }
        armTimeout = Runnable {
            if (TimerController.state.value.state == RunState.ARMED) {
                TimerController.setDormant(applicationContext)
                stopShake() // save battery; flip away & back to re-arm
            }
        }.also { main.postDelayed(it, Pomodoro.ARMING_WINDOW_MS) }
    }

    // --- glyph ---

    private fun initGlyph() {
        gm = GlyphMatrixManager.getInstance(applicationContext)
        gm?.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                val r = runCatching { gm?.register(Glyph.DEVICE_25111p) }
                Log.d(TAG, "glyph connected, register=${r.getOrNull()} err=${r.exceptionOrNull()}")
                renderFrame()
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        })
    }

    /** Renders the current frame and returns how long to wait before the next render. */
    private fun renderFrame(): Long {
        // Only light the matrix when the phone is face-down (matrix facing up).
        if (!faceDown) {
            setFrame(MatrixRenderer.blank())
            return 500L
        }

        val snap = TimerController.state.value

        if (snap.marker != Marker.NONE) {
            val icon = when (snap.marker) {
                Marker.PLAY -> IconStore.Icon.PLAY
                Marker.PAUSE -> IconStore.Icon.PAUSE
                else -> IconStore.Icon.RESET
            }
            val plan = IconStore.animPlan(applicationContext, icon)
            val durMs = IconStore.frameDurationMs(applicationContext).coerceAtLeast(20)
            val animLen = maxOf(plan.cycleMs, MARKER_MIN_MS)
            val elapsed = SystemClock.elapsedRealtime() - snap.markerStartElapsed
            if (elapsed < animLen) {
                val on = floorBrightness(brightnessActive)
                val cells = plan.frameAt(elapsed)
                setFrame(IntArray(cells.size) { if (cells[it]) on else 0 })
                return durMs.coerceIn(20, 250).toLong()
            }
        }

        val dim = snap.state == RunState.PAUSED
        val brightness = floorBrightness(if (dim) brightnessPaused else brightnessActive)
        val n = MatrixRenderer.size()
        when {
            snap.state == RunState.DORMANT || snap.state == RunState.ARMED ->
                setFrame(MatrixRenderer.blank())

            snap.phase == Phase.WORK ->
                setFrame(DigitFont.renderNumber(applicationContext, minutesText(), n, brightness))

            else ->
                setFrame(
                    MatrixRenderer.merge(
                        DigitFont.renderNumber(applicationContext, minutesText(), n, brightness),
                        MatrixRenderer.edge(brightness),
                    )
                )
        }
        return 250L
    }

    /** LEDs don't light below ~65, so a positive brightness is lifted into the visible range. */
    private fun floorBrightness(b: Int): Int = if (b <= 0) 0 else b.coerceIn(MIN_VISIBLE, 255)

    private fun minutesText(): String {
        val remaining = TimerController.remainingMs(System.currentTimeMillis())
        return ceil(remaining / 60_000.0).toInt().toString()
    }

    private fun setFrame(frame: IntArray) {
        runCatching { gm?.setMatrixFrame(frame) }
            .onFailure { Log.w(TAG, "setMatrixFrame failed", it) }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                Log.d(TAG, "toy event: $event")
                when (event) {
                    GlyphToy.EVENT_AOD -> renderFrame()
                    GlyphToy.EVENT_CHANGE -> onShakeTrigger() // long-press = same toggle
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(handler)

    private companion object {
        const val TAG = "PomodoroToy"
        const val MARKER_MIN_MS = 1000L
        /** Gravity Z below this (m/s²) means screen-down & roughly flat → matrix faces up. */
        const val FACE_DOWN_Z = -9f
        /** LEDs are dark below roughly this value. */
        const val MIN_VISIBLE = 65
    }
}
