package org.stuhi.glyphpomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules the exact phase-end alert via AlarmManager — survives the app being killed. */
object PomodoroAlarm {

    fun scheduleOrCancel(ctx: Context) {
        val s = TimerController.state.value
        if (s.state == RunState.RUNNING) schedule(ctx, s.endTimeMillis) else cancel(ctx)
    }

    private fun schedule(ctx: Context, atMillis: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pending(ctx))
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(ctx))
        }
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, PhaseEndReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
