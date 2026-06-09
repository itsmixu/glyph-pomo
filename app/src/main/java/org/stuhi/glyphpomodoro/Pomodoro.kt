package org.stuhi.glyphpomodoro

/** Classic Pomodoro phases. Default durations are 25 / 5 / 15. */
enum class Phase(val defaultMinutes: Int) {
    WORK(25),
    SHORT_BREAK(5),
    LONG_BREAK(15),
}

/** Lifecycle of the timer as shown on the matrix. */
enum class RunState {
    /** Idle, nothing scheduled. Matrix is blank. */
    DORMANT,
    /** Toy just selected: blank, listening for the first shake (10 s window). */
    ARMED,
    RUNNING,
    PAUSED,
}

object Pomodoro {
    /** How long we wait for the first shake before going dormant. */
    const val ARMING_WINDOW_MS = 10_000L

    /** A long break replaces the short break after this many work blocks. */
    const val WORK_BLOCKS_PER_LONG_BREAK = 4

    /** Given the phase that just finished, what comes next. */
    fun nextPhase(finished: Phase, completedWorkBlocks: Int): Phase = when (finished) {
        Phase.WORK ->
            if (completedWorkBlocks % WORK_BLOCKS_PER_LONG_BREAK == 0) Phase.LONG_BREAK
            else Phase.SHORT_BREAK
        else -> Phase.WORK
    }
}
