package org.stuhi.glyphpomodoro

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A play/pause/reset icon (or animation) flashed on the matrix when the mode changes. */
enum class Marker { NONE, PLAY, PAUSE, RESET }

/** Immutable snapshot of the timer. Correctness comes from [endTimeMillis], never tick-counting. */
data class TimerSnapshot(
    val state: RunState = RunState.DORMANT,
    val phase: Phase = Phase.WORK,
    val endTimeMillis: Long = 0L,
    val remainingAtPauseMs: Long = 0L,
    val completedWorkBlocks: Int = 0,
    val marker: Marker = Marker.NONE,
    /** elapsedRealtime when the marker started, so the renderer can drive the animation. */
    val markerStartElapsed: Long = 0L,
)

/**
 * Single source of truth, shared by every component (all in one process).
 * The engine service mutates it; the toy service and UI observe it.
 * Always derive the remaining time from the wall clock so the display is correct at any
 * repaint rate and survives the service being rebound.
 */
object TimerController {

    private val _state = MutableStateFlow(TimerSnapshot())
    val state: StateFlow<TimerSnapshot> = _state

    fun remainingMs(now: Long): Long = _state.value.let {
        when (it.state) {
            RunState.RUNNING -> (it.endTimeMillis - now).coerceAtLeast(0)
            RunState.PAUSED -> it.remainingAtPauseMs
            else -> 0L
        }
    }

    fun phaseTotalMs(): Long = _state.value.phase.defaultMinutes * 60_000L

    fun setArmed(ctx: Context) = mutate(ctx) { it.copy(state = RunState.ARMED) }

    fun setDormant(ctx: Context) = mutate(ctx) { TimerSnapshot(completedWorkBlocks = it.completedWorkBlocks) }

    fun reset(ctx: Context) = mutate(ctx) { TimerSnapshot().withMarker(Marker.RESET) }

    /**
     * The unified control gesture (shake / volume / tap / long-press).
     * Starts a work block when armed, otherwise toggles pause/resume.
     * @return true if a work session just started (so the caller can schedule the alarm).
     */
    fun onTrigger(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        return when (_state.value.state) {
            RunState.ARMED, RunState.DORMANT -> {
                startPhase(ctx, Phase.WORK, _state.value.completedWorkBlocks, now); true
            }
            RunState.RUNNING -> { pause(ctx, now); false }
            RunState.PAUSED -> { resume(ctx, now); false }
        }
    }

    /** Advance to the next phase (called when the current block ends). */
    fun advance(ctx: Context, now: Long = System.currentTimeMillis()) {
        val s = _state.value
        val completed = if (s.phase == Phase.WORK) s.completedWorkBlocks + 1 else s.completedWorkBlocks
        startPhase(ctx, Pomodoro.nextPhase(s.phase, completed), completed, now)
    }

    private fun startPhase(ctx: Context, phase: Phase, completed: Int, now: Long) = mutate(ctx) {
        // Work = "continue" (play); breaks = "pause/rest".
        val marker = if (phase == Phase.WORK) Marker.PLAY else Marker.PAUSE
        it.copy(
            state = RunState.RUNNING,
            phase = phase,
            endTimeMillis = now + phase.defaultMinutes * 60_000L,
            remainingAtPauseMs = 0L,
            completedWorkBlocks = completed,
        ).withMarker(marker)
    }

    private fun pause(ctx: Context, now: Long) = mutate(ctx) {
        it.copy(state = RunState.PAUSED, remainingAtPauseMs = (it.endTimeMillis - now).coerceAtLeast(0))
            .withMarker(Marker.PAUSE)
    }

    private fun resume(ctx: Context, now: Long) = mutate(ctx) {
        it.copy(state = RunState.RUNNING, endTimeMillis = now + it.remainingAtPauseMs, remainingAtPauseMs = 0L)
            .withMarker(Marker.PLAY)
    }

    private fun TimerSnapshot.withMarker(m: Marker) =
        copy(marker = m, markerStartElapsed = SystemClock.elapsedRealtime())

    private inline fun mutate(ctx: Context, f: (TimerSnapshot) -> TimerSnapshot) {
        _state.value = f(_state.value)
        persist(ctx)
    }

    // --- persistence (survives the service being rebound / process death) ---

    fun persist(ctx: Context) {
        val s = _state.value
        prefs(ctx).edit()
            .putInt("state", s.state.ordinal)
            .putInt("phase", s.phase.ordinal)
            .putLong("endTime", s.endTimeMillis)
            .putLong("remainingAtPause", s.remainingAtPauseMs)
            .putInt("completed", s.completedWorkBlocks)
            .apply()
    }

    fun restore(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains("state")) return
        _state.value = TimerSnapshot(
            state = RunState.entries[p.getInt("state", 0)],
            phase = Phase.entries[p.getInt("phase", 0)],
            endTimeMillis = p.getLong("endTime", 0L),
            remainingAtPauseMs = p.getLong("remainingAtPause", 0L),
            completedWorkBlocks = p.getInt("completed", 0),
        )
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("timer", Context.MODE_PRIVATE)
}
