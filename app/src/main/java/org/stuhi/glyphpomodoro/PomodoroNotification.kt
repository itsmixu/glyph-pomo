package org.stuhi.glyphpomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * The ongoing timer notification. Shows the phase + a live countdown (via the system
 * chronometer, so it ticks with no foreground service) and Pause/Resume + Reset actions.
 * Updated by whoever changes the timer; cancels itself when idle.
 */
object PomodoroNotification {

    const val ACTION_TOGGLE = "org.stuhi.glyphpomodoro.action.TOGGLE"
    const val ACTION_RESET = "org.stuhi.glyphpomodoro.action.RESET"

    private const val CHANNEL = "pomodoro_timer"
    private const val ID = 42

    fun update(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val s = TimerController.state.value
        if (s.state != RunState.RUNNING && s.state != RunState.PAUSED) {
            nm.cancel(ID)
            return
        }

        val running = s.state == RunState.RUNNING
        val phase = s.phase.name.replace('_', ' ')
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.toy_preview)
            .setContentTitle(phase)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (running) {
            b.setUsesChronometer(true)
            b.setChronometerCountDown(true)
            b.setWhen(s.endTimeMillis)
            b.setShowWhen(true)
            b.setContentText("Running")
            b.addAction(android.R.drawable.ic_media_pause, "Pause", pending(ctx, ACTION_TOGGLE, 1))
        } else {
            val remaining = TimerController.remainingMs(System.currentTimeMillis())
            b.setContentText("Paused · ${fmt(remaining)}")
            b.addAction(android.R.drawable.ic_media_play, "Resume", pending(ctx, ACTION_TOGGLE, 1))
        }
        b.addAction(android.R.drawable.ic_menu_revert, "Reset", pending(ctx, ACTION_RESET, 2))

        nm.notify(ID, b.build())
    }

    private fun fmt(ms: Long): String {
        val sec = (ms / 1000).toInt()
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    private fun ensureChannel(nm: NotificationManager) {
        val ch = NotificationChannel(CHANNEL, "Pomodoro timer", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
