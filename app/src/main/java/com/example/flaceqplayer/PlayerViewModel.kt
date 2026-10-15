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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    fun initController(context: Context) {
        if (mediaController != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            _uiState.update {
                                it.copy(
                                    durationMs = duration.coerceAtLeast(0L),
                                    audioSpecs = PlaybackService.latestAudioSpecs
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

    fun playFlacFile(context: Context, uri: Uri) {
        val metadata = MetadataExtractor.extract(context, uri)
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
    }

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

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        mediaController?.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    fun skipNext() = mediaController?.seekToNextMediaItem()
    fun skipPrevious() = mediaController?.seekToPreviousMediaItem()

    fun setEqSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isEqOpen = visible) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }
}
