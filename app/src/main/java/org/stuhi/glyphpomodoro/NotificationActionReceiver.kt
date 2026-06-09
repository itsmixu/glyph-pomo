package org.stuhi.glyphpomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Pause/Resume and Reset buttons on the timer notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TimerController.restore(context)
        when (intent?.action) {
            PomodoroNotification.ACTION_TOGGLE -> TimerController.onTrigger(context)
            PomodoroNotification.ACTION_RESET -> TimerController.reset(context)
        }
        PomodoroAlarm.scheduleOrCancel(context)
        PomodoroNotification.update(context)
    }
}
