package com.example.flaceqplayer

import android.graphics.Bitmap

data class PlayerUiState(
    val title: String = "No FLAC Loaded",
    val artist: String = "Tap the folder icon to begin",
    val album: String = "",
    val coverBitmap: Bitmap? = null,
    val audioSpecs: String = "FLAC Hi-Res Player",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val isEqOpen: Boolean = false,
    val errorMessage: String? = null   // Surface load errors via Snackbar
)
