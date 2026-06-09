package org.stuhi.glyphpomodoro

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** A short tick for start/pause/resume feedback. */
object Haptics {
    fun tick(ctx: Context) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
