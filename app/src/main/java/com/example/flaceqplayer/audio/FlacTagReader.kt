package com.example.flaceqplayer.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.flaceqplayer.model.FlacTrack
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin binary FLAC reader extracting STREAMINFO, VORBIS_COMMENT (including LYRICS),
 * and METADATA_BLOCK_PICTURE directly from ContentResolver streams.
 */
object FlacTagReader {

    private const val TAG = "FlacTagReader"
    private const val BLOCK_STREAMINFO = 0
    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_PICTURE = 6
    private const val PICTURE_TYPE_FRONT_COVER = 3
    private const val MAX_METADATA_BLOCK_BYTES = 24 * 1024 * 1024

    class ParsedFlacInfo(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val date: String,
        val genre: String,
        val trackNumber: String,
        val discNumber: String,
        val sampleRateHz: Int,
        val channels: Int,
        val bitDepth: Int,
        val durationMs: Long,
        val bitrateKbps: Int,
        val hasCover: Boolean,
        val coverMime: String,
        val embeddedLyrics: String,
        val coverBytes: ByteArray? = null
    )

    fun readTrack(
        context: Context,
        id: String,
        uri: Uri,
        fileName: String,
        filePath: String = "",
        fileSizeBytes: Long = 0L,
        extractCoverBytes: Boolean = false
    ): FlacTrack? {
        val info = readStream(context, uri, fileName, fileSizeBytes, extractCoverBytes) ?: return null
        return FlacTrack(
            id = id,
            uri = uri,
            fileName = fileName,
            filePath = filePath,
            title = info.title,
            artist = info.artist,
            album = info.album,
            albumArtist = info.albumArtist,
            date = info.date,
            genre = info.genre,
            trackNumber = info.trackNumber,
            discNumber = info.discNumber,
            sampleRateHz = info.sampleRateHz,
            bitDepth = info.bitDepth,
            channels = info.channels,
            durationMs = info.durationMs,
            fileSizeBytes = fileSizeBytes,
            bitrateKbps = info.bitrateKbps,
            hasCover = info.hasCover,
            coverMime = info.coverMime,
            embeddedLyrics = info.embeddedLyrics
        )
    }

    fun extractCoverBitmap(context: Context, uri: Uri, maxSizePx: Int = 720): Bitmap? {
        return try {
            val info = readStream(
                context = context,
                uri = uri,
                fallbackName = "track",
                fileSizeBytes = 0L,
                extractCoverBytes = true
            )
            val bytes = info?.coverBytes ?: return null
            decodeSampledBitmap(bytes, maxSizePx)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract cover bitmap: ${e.message}")
            null
        }
    }

    fun decodeSampledBitmap(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var inSampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim > maxSizePx && maxSizePx > 0) {
            while (maxDim / (inSampleSize * 2) >= maxSizePx) {
                inSampleSize *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun readStream(
        context: Context,
        uri: Uri,
        fallbackName: String,
        fileSizeBytes: Long,
        extractCoverBytes: Boolean
    ): ParsedFlacInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val bis = BufferedInputStream(raw, 32 * 1024)
                parseFlacStream(bis, fallbackName, fileSizeBytes, extractCoverBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading FLAC stream for $uri: ${e.message}")
            null
        }
    }

    private fun parseFlacStream(
        input: InputStream,
        fallbackName: String,
        fileSizeBytes: Long,
        extractCoverBytes: Boolean
    ): ParsedFlacInfo? {
        val header = ByteArray(4)
        if (!readExact(input, header, 0, 4)) return null

        // Skip optional prepended ID3v2 header
        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val rest = ByteArray(6)
            if (!readExact(input, rest, 0, 6)) return null
            val size = ((rest[2].toInt() and 0x7F) shl 21) or
                ((rest[3].toInt() and 0x7F) shl 14) or
                ((rest[4].toInt() and 0x7F) shl 7) or
                (rest[5].toInt() and 0x7F)
            val hasFooter = (rest[1].toInt() and 0x10) != 0
            skipExact(input, (size + if (hasFooter) 10 else 0).toLong())
            if (!readExact(input, header, 0, 4)) return null
        }

        if (header[0] != 'f'.code.toByte() ||
            header[1] != 'L'.code.toByte() ||
            header[2] != 'a'.code.toByte() ||
            header[3] != 'C'.code.toByte()
        ) {
            return null
        }

        var sampleRate = 0
        var channels = 2
        var bitDepth = 0
        var totalSamples = 0L
        val tags = LinkedHashMap<String, MutableList<String>>()
        var bestCoverMime = ""
        var bestCoverIsFront = false
        var bestCoverBytes: ByteArray? = null

        var isLast = false
        val blockHdr = ByteArray(4)
        while (!isLast) {
            if (!readExact(input, blockHdr, 0, 4)) break
            val b0 = blockHdr[0].toInt() and 0xFF
            isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val length = ((blockHdr[1].toInt() and 0xFF) shl 16) or
                ((blockHdr[2].toInt() and 0xFF) shl 8) or
                (blockHdr[3].toInt() and 0xFF)

            if (length < 0 || length > MAX_METADATA_BLOCK_BYTES) break

            val shouldReadPayload = blockType == BLOCK_STREAMINFO ||
                blockType == BLOCK_VORBIS_COMMENT ||
                blockType == BLOCK_PICTURE

            if (!shouldReadPayload) {
                skipExact(input, length.toLong())
                continue
            }

            val payload = ByteArray(length)
            if (!readExact(input, payload, 0, length)) break

            when (blockType) {
                BLOCK_STREAMINFO -> {
                    if (payload.size >= 18) {
                        var packed = 0L
                        for (i in 10..17) {
                            packed = (packed shl 8) or (payload[i].toLong() and 0xFFL)
                        }
                        sampleRate = ((packed ushr 44) and 0xFFFFFL).toInt()
                        channels = (((packed ushr 41) and 0x7L).toInt()) + 1
                        bitDepth = (((packed ushr 36) and 0x1FL).toInt()) + 1
                        totalSamples = packed and 0xFFFFFFFFFL
                    }
                }
                BLOCK_VORBIS_COMMENT -> parseVorbisComment(payload, tags)
                BLOCK_PICTURE -> {
                    val pic = parsePictureBlock(payload, readBytes = extractCoverBytes)
                    if (pic != null) {
                        if (bestCoverMime.isEmpty() || (!bestCoverIsFront && pic.pictureType == PICTURE_TYPE_FRONT_COVER)) {
                            bestCoverMime = pic.mimeType
                            bestCoverIsFront = pic.pictureType == PICTURE_TYPE_FRONT_COVER
                            if (extractCoverBytes) {
                                bestCoverBytes = pic.bytes
                            }
                        }
                    }
                }
            }
        }

        val durationMs = if (sampleRate > 0 && totalSamples > 0L) {
            (totalSamples * 1000L) / sampleRate
        } else 0L

        val bitrateKbps = if (durationMs > 0L && fileSizeBytes > 0L) {
            ((fileSizeBytes * 8L) / durationMs).toInt()
        } else 0

        val baseName = fallbackName.substringBeforeLast('.')
        val title = firstTag(tags, "TITLE").ifBlank { baseName }
        val artist = firstTag(tags, "ARTIST", "ARTISTS", "ALBUMARTIST", "COMPOSER")
            .ifBlank { "Unknown Artist" }
        val album = firstTag(tags, "ALBUM").ifBlank { "Unknown Album" }
        val albumArtist = firstTag(tags, "ALBUMARTIST", "ALBUM ARTIST")
        val date = firstTag(tags, "DATE", "YEAR", "ORIGINALDATE")
        val genre = firstTag(tags, "GENRE")
        val trackNumber = firstTag(tags, "TRACKNUMBER", "TRACK")
        val discNumber = firstTag(tags, "DISCNUMBER", "DISC")
        val lyrics = pickLyrics(tags)

        return ParsedFlacInfo(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            date = date,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber,
            sampleRateHz = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            hasCover = bestCoverMime.isNotEmpty(),
            coverMime = bestCoverMime,
            embeddedLyrics = lyrics,
            coverBytes = bestCoverBytes
        )
    }

    private fun parseVorbisComment(payload: ByteArray, out: MutableMap<String, MutableList<String>>) {
        if (payload.size < 8) return
        var pos = 0
        val vendorLen = readIntLE(payload, pos)
        pos += 4 + vendorLen
        if (vendorLen < 0 || pos + 4 > payload.size) return
        val count = readIntLE(payload, pos)
        pos += 4
        if (count < 0) return

        for (i in 0 until count) {
            if (pos + 4 > payload.size) break
            val cLen = readIntLE(payload, pos)
            pos += 4
            if (cLen < 0 || pos + cLen > payload.size) break
            val comment = String(payload, pos, cLen, StandardCharsets.UTF_8)
            pos += cLen
            val eq = comment.indexOf('=')
            if (eq <= 0) continue
            val key = comment.substring(0, eq).trim().uppercase()
            val value = comment.substring(eq + 1)
            out.getOrPut(key) { mutableListOf() }.add(value)
        }
    }

    private class PictureBlock(
        val pictureType: Int,
        val mimeType: String,
        val bytes: ByteArray?
    )

    private fun parsePictureBlock(payload: ByteArray, readBytes: Boolean): PictureBlock? {
        if (payload.size < 32) return null
        var pos = 0
        val picType = readIntBE(payload, pos); pos += 4
        val mimeLen = readIntBE(payload, pos); pos += 4
        if (mimeLen < 0 || pos + mimeLen + 4 > payload.size) return null
        val mime = String(payload, pos, mimeLen, StandardCharsets.UTF_8)
        pos += mimeLen
        val descLen = readIntBE(payload, pos); pos += 4
        if (descLen < 0 || pos + descLen + 20 > payload.size) return null
        pos += descLen + 16 // skip desc + width, height, depth, colors
        val dataLen = readIntBE(payload, pos); pos += 4
        if (dataLen <= 0 || pos + dataLen > payload.size) return null
        val bytes = if (readBytes) payload.copyOfRange(pos, pos + dataLen) else null
        return PictureBlock(picType, mime.ifBlank { "image/jpeg" }, bytes)
    }

    private fun firstTag(tags: Map<String, List<String>>, vararg keys: String): String {
        for (key in keys) {
            val values = tags[key.uppercase()] ?: continue
            val joined = values.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            if (joined.isNotEmpty()) return joined
        }
        return ""
    }

    private fun pickLyrics(tags: Map<String, List<String>>): String {
        for (key in listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS")) {
            val values = tags[key] ?: continue
            val text = values.filter { it.isNotBlank() }.joinToString("\n").trim()
            if (text.isNotEmpty()) return text
        }
        return ""
    }

    private fun readIntLE(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readIntBE(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }

    private fun readExact(input: InputStream, buf: ByteArray, off: Int, len: Int): Boolean {
        var total = 0
        while (total < len) {
            val r = input.read(buf, off + total, len - total)
            if (r < 0) return false
            total += r
        }
        return true
    }

    private fun skipExact(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read < 0) throw EOFException("Unexpected EOF skipping FLAC block")
                remaining -= read
            }
        }
    }
}
