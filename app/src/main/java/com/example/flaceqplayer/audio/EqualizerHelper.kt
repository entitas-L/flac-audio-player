package com.example.flaceqplayer.audio

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Immutable band snapshot – use copy() to update
data class EqBand(
    val index: Short,
    val centerFreqHz: Int,
    val currentLevelMb: Short
)

val EqBand.gainDb: Float get() = currentLevelMb / 100f

data class EqPreset(
    val name: String,
    val gains: List<Float> // Normalized -1.0 (min) .. +1.0 (max)
)

object EqualizerHelper {
    private const val TAG = "EqualizerHelper"
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val lock = Any()

    var isEnabled: Boolean = false
        private set

    var minLevelMb: Int = -1500
        private set
    var maxLevelMb: Int = 1500
        private set

    private val defaultBands = listOf(
        EqBand(0, 60, 0),
        EqBand(1, 230, 0),
        EqBand(2, 910, 0),
        EqBand(3, 3600, 0),
        EqBand(4, 14000, 0)
    )

    // Reactive band list:
    // Updated ONLY on attachSession() and applyPreset() — NOT on individual
    // setBandGainMb() calls — so Compose sliders drag smoothly without
    // triggering global recomposition on every finger movement.
    private val _bandsState = MutableStateFlow(defaultBands)
    val bandsState: StateFlow<List<EqBand>> = _bandsState.asStateFlow()

    val bands: List<EqBand> get() = _bandsState.value

    // Remembers per-band levels set via sliders/presets; survives session re-init
    private val persistedLevelsMb = mutableMapOf<Int, Short>()

    val defaultPresets = listOf(
        EqPreset("Flat", listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
        EqPreset("Bass Boost", listOf(0.75f, 0.45f, 0.0f, 0.0f, 0.05f)),
        EqPreset("Vocal / J-Pop", listOf(-0.15f, 0.15f, 0.65f, 0.50f, 0.20f)),
        EqPreset("Rock / Metal", listOf(0.60f, 0.30f, -0.20f, 0.40f, 0.65f)),
        EqPreset("Pop", listOf(-0.10f, 0.30f, 0.60f, 0.30f, -0.20f)),
        EqPreset("Hi-Res Air", listOf(0.10f, 0.0f, 0.20f, 0.60f, 0.85f))
    )

    fun attachSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        synchronized(lock) {
            if (audioSessionId == currentSessionId) return
            currentSessionId = audioSessionId
            try {
                equalizer?.release()
                val eq = Equalizer(0, audioSessionId).apply { enabled = isEnabled }
                val range = eq.bandLevelRange
                minLevelMb = range[0].toInt()
                maxLevelMb = range[1].toInt()

                val newBands = (0 until eq.numberOfBands).map { i ->
                    val idx = i.toShort()
                    val freqHz = eq.getCenterFreq(idx) / 1000
                    val level: Short = persistedLevelsMb[i]?.let { saved ->
                        val clamped = saved.toInt().coerceIn(minLevelMb, maxLevelMb).toShort()
                        eq.setBandLevel(idx, clamped)
                        clamped
                    } ?: eq.getBandLevel(idx)
                    EqBand(idx, freqHz, level)
                }
                equalizer = eq
                _bandsState.value = newBands
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer init failed for session $audioSessionId: ${e.message}")
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        synchronized(lock) {
            try {
                equalizer?.enabled = enabled
            } catch (e: Exception) {
                Log.w(TAG, "Failed to toggle equalizer: ${e.message}")
            }
        }
    }

    /**
     * Updates a single band's gain on the hardware equalizer without emitting to
     * [bandsState], preserving uninterrupted slider drag gestures.
     */
    fun setBandGainMb(bandIndex: Int, levelMb: Short) {
        synchronized(lock) {
            val current = _bandsState.value
            if (bandIndex !in current.indices) return
            val clamped = levelMb.toInt().coerceIn(minLevelMb, maxLevelMb).toShort()
            try {
                equalizer?.setBandLevel(current[bandIndex].index, clamped)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set band $bandIndex level: ${e.message}")
            }
            persistedLevelsMb[bandIndex] = clamped
        }
    }

    fun applyPreset(preset: EqPreset) {
        val newBands = synchronized(lock) {
            _bandsState.value.mapIndexed { i, band ->
                val ratio = preset.gains.getOrElse(i) { 0f }
                val rawTarget = if (ratio >= 0f) ratio * maxLevelMb else ratio * (-minLevelMb)
                val targetMb = rawTarget.toInt()
                    .coerceIn(minLevelMb, maxLevelMb)
                    .toShort()
                try {
                    equalizer?.setBandLevel(band.index, targetMb)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply preset band $i: ${e.message}")
                }
                persistedLevelsMb[i] = targetMb
                band.copy(currentLevelMb = targetMb)
            }
        }
        _bandsState.value = newBands
    }

    fun release() {
        synchronized(lock) {
            try {
                equalizer?.release()
            } catch (_: Exception) {
            }
            equalizer = null
            currentSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }
}
