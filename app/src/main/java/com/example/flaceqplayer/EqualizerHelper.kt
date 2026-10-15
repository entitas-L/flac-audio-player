package com.example.flaceqplayer

import android.media.audiofx.Equalizer
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
    val gains: List<Float>  // Normalized –1.0 (min) … +1.0 (max)
)

object EqualizerHelper {
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val lock = Any()

    var isEnabled: Boolean = true
        private set

    var minLevelMb: Int = -1500
        private set
    var maxLevelMb: Int = 1500
        private set

    // ── Reactive band list ─────────────────────────────────────────────────
    // Updated ONLY on:
    //   • attachSession()  → new audio session
    //   • applyPreset()    → preset applied
    // NOT updated on individual setBandGainMb() to avoid Compose recomposition
    // loops while the user is dragging a slider.
    private val _bandsState = MutableStateFlow<List<EqBand>>(emptyList())
    val bandsState: StateFlow<List<EqBand>> = _bandsState.asStateFlow()

    // Imperative getter for non-Compose callers
    val bands: List<EqBand> get() = _bandsState.value

    // Remembers per-band levels set via the slider; survives session re-init
    private val persistedLevelsMb = mutableMapOf<Int, Short>()

    val defaultPresets = listOf(
        EqPreset("Flat",        listOf( 0.0f,  0.0f,  0.0f,  0.0f,  0.0f)),
        EqPreset("Bass Boost",  listOf( 0.8f,  0.5f,  0.0f,  0.0f, -0.1f)),
        EqPreset("Rock",        listOf( 0.6f,  0.3f, -0.2f,  0.4f,  0.7f)),
        EqPreset("Pop",         listOf(-0.1f,  0.3f,  0.6f,  0.3f, -0.2f)),
        EqPreset("Vocal",       listOf(-0.3f,  0.1f,  0.7f,  0.5f,  0.0f)),
        EqPreset("Hi-Res Air",  listOf( 0.1f,  0.0f,  0.2f,  0.6f,  0.9f))
    )

    // ── Session management ─────────────────────────────────────────────────
    fun attachSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
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
                    // Restore previously-set gain if available
                    val level: Short = persistedLevelsMb[i]?.let { saved ->
                        eq.setBandLevel(idx, saved)
                        saved
                    } ?: eq.getBandLevel(idx)
                    EqBand(idx, freqHz, level)
                }
                equalizer = eq
                _bandsState.value = newBands       // ← triggers Compose recomposition
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Controls ───────────────────────────────────────────────────────────
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        synchronized(lock) {
            try { equalizer?.enabled = enabled } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Update a single band's gain on the HARDWARE equalizer.
     *
     * Intentionally does NOT update [bandsState] / [_bandsState] so that the
     * Compose slider can drag smoothly without triggering a global recomposition
     * on every millimetre of finger movement.
     *
     * [persistedLevelsMb] is kept current so the value is restored if the audio
     * session is ever re-initialised (e.g. headphone disconnect → reconnect).
     */
    fun setBandGainMb(bandIndex: Int, levelMb: Short) {
        synchronized(lock) {
            val current = _bandsState.value
            if (bandIndex !in current.indices) return
            try {
                equalizer?.setBandLevel(current[bandIndex].index, levelMb)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            persistedLevelsMb[bandIndex] = levelMb
        }
    }

    fun applyPreset(preset: EqPreset) {
        val newBands = synchronized(lock) {
            _bandsState.value.mapIndexed { i, band ->
                val ratio = preset.gains.getOrElse(i) { 0f }
                val targetMb = (ratio * maxLevelMb).toInt()
                    .coerceIn(minLevelMb, maxLevelMb).toShort()
                try {
                    equalizer?.setBandLevel(band.index, targetMb)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                persistedLevelsMb[i] = targetMb
                band.copy(currentLevelMb = targetMb)
            }
        }
        _bandsState.value = newBands   // ← triggers UI refresh for preset visualizer
    }

    fun release() {
        synchronized(lock) {
            equalizer?.release()
            equalizer = null
            currentSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }
}
