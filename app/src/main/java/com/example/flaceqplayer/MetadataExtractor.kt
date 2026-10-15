package com.example.flaceqplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri

data class FlacTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val channels: Int,       // Always 2 (Stereo default); MediaMetadataRetriever has no channel key
    val durationMs: Long,
    val coverBitmap: Bitmap?
) {
    val technicalSpecsBadge: String
        get() {
            val khz = if (sampleRateHz > 0)
                String.format("%.1f kHz", sampleRateHz / 1000f)
            else "—"
            val bit = if (bitDepth > 0) "$bitDepth-bit" else "—"
            val ch = if (channels >= 2) "Stereo" else "Mono"
            return "FLAC • $khz • $bit • $ch"
        }
}

object MetadataExtractor {
    // Down-sample embedded art to this max size for fast Palette generation on low-end phones
    private const val MAX_COVER_PX = 512

    fun extract(context: Context, uri: Uri): FlacTrackMetadata {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        var title = "Unknown Title"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var sampleRate = 0
        var bitDepth = 0
        var channels = 2      // default: Stereo
        var duration = 0L

        try {
            retriever.setDataSource(context, uri)

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown Title"
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()?.let { sampleRate = it }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                ?.toIntOrNull()?.let { bitDepth = it }

            // NOTE: METADATA_KEY_NUM_TRACKS = number of tracks in the album (NOT audio channels).
            // MediaMetadataRetriever has no public key for channel count.
            // The accurate value comes from ExoPlayer's audio format post-decode (used in PlaybackService).

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { duration = it }

            // Embedded album art – down-sampled for Palette and memory efficiency
            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (raw != null) {
                    bitmap = if (raw.width > MAX_COVER_PX || raw.height > MAX_COVER_PX) {
                        val scale = MAX_COVER_PX.toFloat() / maxOf(raw.width, raw.height)
                        val w = (raw.width * scale).toInt().coerceAtLeast(1)
                        val h = (raw.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(raw, w, h, true).also { raw.recycle() }
                    } else {
                        raw
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return FlacTrackMetadata(
            title = title,
            artist = artist,
            album = album,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            durationMs = duration,
            coverBitmap = bitmap
        )
    }
}
