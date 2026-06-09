package org.stuhi.glyphpomodoro

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Tunable config for shake detection and matrix brightness. */
data class Settings(
    /** Accelerometer magnitude (m/s²) a shake must exceed to start/pause. */
    val shakeThreshold: Float = 12f,
    /** Magnitude a sustained shake must exceed to count toward a reset. */
    val resetShakeThreshold: Float = 8f,
    /** How long the sustained shake must last (ms) to reset. */
    val resetHoldMs: Int = 970,
    /** LED brightness while running (0–255). */
    val brightness: Int = 255,
    /** LED brightness while paused (0–255). */
    val pausedBrightness: Int = 70,
)

class SettingsRepo(private val ctx: Context) {

    val flow: Flow<Settings> = ctx.dataStore.data.map { p ->
        Settings(
            shakeThreshold = p[SHAKE] ?: 12f,
            resetShakeThreshold = p[RESET_SHAKE] ?: 8f,
            resetHoldMs = p[RESET_HOLD] ?: 970,
            brightness = p[BRIGHT] ?: 255,
            pausedBrightness = p[BRIGHT_PAUSED] ?: 70,
        )
    }

    suspend fun setShakeThreshold(v: Float) = ctx.dataStore.edit { it[SHAKE] = v }
    suspend fun setResetShakeThreshold(v: Float) = ctx.dataStore.edit { it[RESET_SHAKE] = v }
    suspend fun setResetHoldMs(v: Int) = ctx.dataStore.edit { it[RESET_HOLD] = v }
    suspend fun setBrightness(v: Int) = ctx.dataStore.edit { it[BRIGHT] = v }
    suspend fun setPausedBrightness(v: Int) = ctx.dataStore.edit { it[BRIGHT_PAUSED] = v }

    private companion object {
        val SHAKE = floatPreferencesKey("shake_threshold")
        val RESET_SHAKE = floatPreferencesKey("reset_shake_threshold")
        val RESET_HOLD = intPreferencesKey("reset_hold_ms")
        val BRIGHT = intPreferencesKey("brightness")
        val BRIGHT_PAUSED = intPreferencesKey("brightness_paused")
    }
}
