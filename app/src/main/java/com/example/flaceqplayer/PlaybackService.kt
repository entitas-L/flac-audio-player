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
        var latestAudioSpecs: String = "FLAC LOSSLESS • 96.0 kHz • 24-bit"
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
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerHelper.attachSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateFlacSpecs(player)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateFlacSpecs(player)
            }
        })

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            EqualizerHelper.attachSession(player.audioSessionId)
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun updateFlacSpecs(player: ExoPlayer) {
        val format = player.audioFormat ?: return
        val khz = String.format(Locale.US, "%.1f", format.sampleRate / 1000f)
        val mime = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "FLAC"
        latestAudioSpecs = "$mime LOSSLESS • $khz kHz • ${format.channelCount} CH"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

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
