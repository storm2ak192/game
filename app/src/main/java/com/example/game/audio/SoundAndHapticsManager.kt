package com.example.game.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Provides lightweight mystery-themed audio cues and tactile haptic feedback
 * controlled by user settings.
 */
class SoundAndHapticsManager(context: Context) {

    private val appContext = context.applicationContext

    fun playTap(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP, 35)
        if (vibrationEnabled) vibrate(18)
    }

    fun playScratchReveal(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) playTone(ToneGenerator.TONE_PROP_ACK, 90)
        if (vibrationEnabled) vibrate(45)
    }

    fun playVoteWarning(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 120)
        if (vibrationEnabled) vibrate(70)
    }

    fun playVictory(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) playTone(ToneGenerator.TONE_PROP_PROMPT, 180)
        if (vibrationEnabled) vibrate(110)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            tg.startTone(toneType, durationMs)
        } catch (_: Exception) {
            // Safe no-op in headless/test environments
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            // Safe no-op
        }
    }
}
