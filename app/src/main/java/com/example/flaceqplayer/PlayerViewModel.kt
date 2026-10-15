package com.example.flaceqplayer

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    // ── MediaController init ───────────────────────────────────────────────
    fun initController(context: Context) {
        if (mediaController != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                mediaController = future.get().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            // Update duration and pick up the accurate decoded audio specs
                            // from PlaybackService (available once ExoPlayer is STATE_READY)
                            val spec = PlaybackService.latestAudioSpecs
                            _uiState.update {
                                it.copy(
                                    durationMs = duration.coerceAtLeast(0L),
                                    audioSpecs = if (spec.isNotEmpty()) spec else it.audioSpecs
                                )
                            }
                        }
                    })
                    _uiState.update { it.copy(volume = volume) }
                }
                startProgressTracker()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    // ── File loading ───────────────────────────────────────────────────────
    /**
     * Loads a FLAC file:
     * 1. Metadata extraction runs on [Dispatchers.IO] (blocking file I/O).
     * 2. UI state update and MediaController calls happen on the main thread.
     */
    fun playFlacFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(errorMessage = null) }

                val metadata = withContext(Dispatchers.IO) {
                    MetadataExtractor.extract(context, uri)
                }

                _uiState.update {
                    it.copy(
                        title = metadata.title,
                        artist = metadata.artist,
                        album = metadata.album,
                        coverBitmap = metadata.coverBitmap,
                        audioSpecs = metadata.technicalSpecsBadge,
                        durationMs = metadata.durationMs
                    )
                }

                mediaController?.let { player ->
                    player.setMediaItem(MediaItem.fromUri(uri))
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Failed to load file: ${e.message}") }
            }
        }
    }

    // ── Progress tracking ──────────────────────────────────────────────────
    private fun startProgressTracker() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    _uiState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(400)
            }
        }
    }

    // ── Playback controls ──────────────────────────────────────────────────
    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        mediaController?.volume = v
        _uiState.update { it.copy(volume = v) }
    }

    /**
     * "Previous" always seeks to the beginning of the current track.
     * (Single-track player has no previous item.)
     */
    fun seekToStart() {
        mediaController?.seekTo(0L)
    }

    /**
     * "Next" is repurposed as a trigger for the file picker in the UI layer.
     * The ViewModel just resets the position so the button has an observable effect.
     * The actual file-picker launch is wired in NowPlayingScreen.
     */
    fun requestNextFile() { /* Signal is handled in the UI via the onOpenFilePicker callback */ }

    fun setEqSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isEqOpen = visible) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }
}
