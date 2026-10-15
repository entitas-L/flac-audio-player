package com.example.flaceqplayer

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.Locale

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        // Empty string → ViewModel keeps the badge from MetadataExtractor until ExoPlayer decodes
        @Volatile
        var latestAudioSpecs: String = ""
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerHelper.attachSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateAudioSpecs(player)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateAudioSpecs(player)
            }
        })

        // Attach EQ to the session created at ExoPlayer init (if already available)
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            EqualizerHelper.attachSession(player.audioSessionId)
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun updateAudioSpecs(player: ExoPlayer) {
        val format = player.audioFormat ?: return
        val khz = String.format(Locale.US, "%.1f", format.sampleRate / 1000f)

        // Decode PCM encoding to human-readable bit depth using standard Android AudioFormat values:
        // ENCODING_PCM_16BIT = 2, ENCODING_PCM_24BIT_PACKED = 21, ENCODING_PCM_32BIT = 22, ENCODING_PCM_FLOAT = 4
        val bitDepth = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> "16-bit"
            21 -> "24-bit"
            22, C.ENCODING_PCM_32BIT -> "32-bit"
            4 -> "32-bit Float"
            else -> "Lossless"
        }
        val ch = if (format.channelCount > 1) "Stereo" else "Mono"

        latestAudioSpecs = "FLAC • $khz kHz • $bitDepth • $ch"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        EqualizerHelper.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
