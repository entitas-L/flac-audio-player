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
    val channels: Int,
    val durationMs: Long,
    val coverBitmap: Bitmap?
) {
    val technicalSpecsBadge: String
        get() {
            val khz = if (sampleRateHz > 0) String.format("%.1f kHz", sampleRateHz / 1000f) else "44.1 kHz"
            val bit = if (bitDepth > 0) "$bitDepth-bit" else "16-bit / 24-bit"
            val ch = if (channels > 1) "Stereo" else "Mono"
            return "FLAC LOSSLESS • $khz • $bit • $ch"
        }
}

object MetadataExtractor {

    fun extract(context: Context, uri: Uri): FlacTrackMetadata {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        var title = "Unknown Title"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var sampleRate = 0
        var bitDepth = 0
        var channels = 2
        var duration = 0L

        try {
            retriever.setDataSource(context, uri)

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown Title"
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                sampleRate = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let {
                bitDepth = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull()?.let {
                channels = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                duration = it
            }

            // Embedded Album Art in FLAC
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
