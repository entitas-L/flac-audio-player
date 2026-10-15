package com.example.flaceqplayer

import android.graphics.Bitmap

data class PlayerUiState(
    val title: String = "No FLAC Loaded",
    val artist: String = "Select a track to play",
    val album: String = "",
    val coverBitmap: Bitmap? = null,
    val audioSpecs: String = "FLAC LOSSLESS • Ready",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val isEqOpen: Boolean = false
)
