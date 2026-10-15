package com.example.flaceqplayer

import android.media.audiofx.Equalizer
import androidx.media3.common.C

data class EqBand(
    val index: Short,
    val centerFreqHz: Int,
    var currentLevelMb: Short
) {
    val gainDb: Float
        get() = currentLevelMb / 100f
}

data class EqPreset(
    val name: String,
    val gains: List<Float> // normalized ratio -1.0 .. +1.0
)

object EqualizerHelper {
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = true
        private set

    var minLevelMb: Int = -1500
        private set
    var maxLevelMb: Int = 1500
        private set

    val bands = mutableListOf<EqBand>()
    var onBandsUpdated: (() -> Unit)? = null

    val defaultPresets = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Boost", listOf(0.8f, 0.5f, 0.0f, 0.0f, -0.1f)),
        EqPreset("Rock", listOf(0.6f, 0.3f, -0.2f, 0.4f, 0.7f)),
        EqPreset("Pop", listOf(-0.1f, 0.3f, 0.6f, 0.3f, -0.2f)),
        EqPreset("Vocal", listOf(-0.3f, 0.1f, 0.7f, 0.5f, 0.0f)),
        EqPreset("Hi-Res Air", listOf(0.1f, 0.0f, 0.2f, 0.6f, 0.9f))
    )

    fun attachSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        try {
            val savedLevels = bands.map { it.currentLevelMb }
            equalizer?.release()

            val eq = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }

            val range = eq.bandLevelRange
            minLevelMb = range[0].toInt()
            maxLevelMb = range[1].toInt()

            bands.clear()
            val numBands = eq.numberOfBands
            for (i in 0 until numBands) {
                val bandIndex = i.toShort()
                val freqHz = eq.getCenterFreq(bandIndex) / 1000
                val level = if (i < savedLevels.size) {
                    eq.setBandLevel(bandIndex, savedLevels[i])
                    savedLevels[i]
                } else {
                    eq.getBandLevel(bandIndex)
                }
                bands.add(EqBand(bandIndex, freqHz, level))
            }
            equalizer = eq
            onBandsUpdated?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        equalizer?.enabled = enabled
        onBandsUpdated?.invoke()
    }

    fun setBandGainMb(bandIndex: Int, levelMb: Short) {
        if (bandIndex !in bands.indices) return
        val band = bands[bandIndex]
        equalizer?.setBandLevel(band.index, levelMb)
        band.currentLevelMb = levelMb
    }

    fun applyPreset(preset: EqPreset) {
        for (i in bands.indices) {
            val ratio = if (i < preset.gains.size) preset.gains[i] else 0f
            val targetMb = (ratio * maxLevelMb).toInt()
                .coerceIn(minLevelMb, maxLevelMb)
                .toShort()
            setBandGainMb(i, targetMb)
        }
        onBandsUpdated?.invoke()
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        currentSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
