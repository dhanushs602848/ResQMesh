package com.resqmesh.app.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.resqmesh.app.data.model.IncidentPriority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResQMesh-AudioAlert"

/**
 * Production Android audio & haptic alert manager.
 * Uses Android platform [ToneGenerator] and [Vibrator] for zero-dependency, 100% offline emergency sound synthesis.
 */
@Singleton
class AndroidAudioAlertManager @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioAlertManager {

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isAudioEnabled: Boolean = true

    override fun playAlert(priority: IncidentPriority) {
        if (!isAudioEnabled) {
            Log.d(TAG, "Audio alert disabled by user preference")
            return
        }

        audioScope.launch {
            try {
                when (priority) {
                    IncidentPriority.CRITICAL -> playCriticalAlarm()
                    IncidentPriority.HIGH -> playHighPriorityWarning()
                    IncidentPriority.NORMAL,
                    IncidentPriority.INFO -> playAdvisoryChirp()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to play audio alert: ${t.message}")
            }
        }
    }

    override fun stopAlert() {
        // No-op for brief tone pulses
    }

    override fun isEnabled(): Boolean = isAudioEnabled

    override fun setEnabled(enabled: Boolean) {
        isAudioEnabled = enabled
    }

    internal suspend fun playCriticalAlarm() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            repeat(3) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 250)
                vibrateDevice(250)
                delay(300)
            }
            toneGenerator.release()
        } catch (t: Throwable) {
            Log.w(TAG, "ToneGenerator fallback for critical alarm: ${t.message}")
        }
    }

    internal suspend fun playHighPriorityWarning() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            repeat(2) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                vibrateDevice(180)
                delay(220)
            }
            toneGenerator.release()
        } catch (t: Throwable) {
            Log.w(TAG, "ToneGenerator fallback for high priority warning: ${t.message}")
        }
    }

    internal suspend fun playAdvisoryChirp() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            vibrateDevice(100)
            delay(150)
            toneGenerator.release()
        } catch (t: Throwable) {
            Log.w(TAG, "ToneGenerator fallback for advisory chirp: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateDevice(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Throwable) {
            // Non-fatal if vibrator service is unavailable
        }
    }
}
