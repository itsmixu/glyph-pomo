package org.stuhi.glyphpomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.VibratorManager

/** Fired by AlarmManager exactly when a block ends: buzz, advance to the next phase, reschedule. */
class PhaseEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TimerController.restore(context)
        vibrate(context)
        TimerController.advance(context)
        PomodoroAlarm.scheduleOrCancel(context)
        PomodoroNotification.update(context)
    }

    private fun vibrate(ctx: Context) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 150, 200), -1))
    }
}
