package com.example.flaceqplayer.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.flaceqplayer.audio.FlacTagReader
import com.example.flaceqplayer.model.FlacTrack
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

object FlacLibraryScanner {

    fun scanDeviceFlac(context: Context): List<FlacTrack> {
        val tracks = mutableListOf<FlacTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "(${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("audio/flac", "%.flac")

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "track.flac"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    if (displayName.startsWith("._")) continue

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val trackId = "ms_$mediaId"
                    val parsed = FlacTagReader.readTrack(
                        context = context,
                        id = trackId,
                        uri = uri,
                        fileName = displayName,
                        filePath = path,
                        fileSizeBytes = sizeBytes,
                        extractCoverBytes = false
                    )
                    if (parsed != null) {
                        tracks.add(parsed)
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
        }

        return tracks.sortedWith(
            compareBy<FlacTrack>(
                { it.album.lowercase(Locale.ROOT) },
                { leadingInt(it.discNumber) },
                { leadingInt(it.trackNumber) },
                { it.title.lowercase(Locale.ROOT) }
            )
        )
    }

    fun readPickedFlac(context: Context, uri: Uri): FlacTrack? {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "picked.flac"
        var fileSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) fileSize = c.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        }

        val trackId = "uri_" + sha1Short(uri.toString())
        return FlacTagReader.readTrack(
            context = context,
            id = trackId,
            uri = uri,
            fileName = displayName,
            filePath = "",
            fileSizeBytes = fileSize,
            extractCoverBytes = false
        )
    }

    fun getOrCacheThumbnail(context: Context, track: FlacTrack): Bitmap? {
        if (!track.hasCover) return null
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "${track.id}.jpg")
        if (file.isFile && file.length() > 0L) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val bmp = FlacTagReader.extractCoverBitmap(context, track.uri, maxSizePx = 180) ?: return null
        try {
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        } catch (_: Exception) {
        }
        return bmp
    }

    private fun leadingInt(raw: String): Int {
        val m = Regex("""^\s*(\d+)""").find(raw)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: 9999
    }

    private fun sha1Short(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return bytes.take(8).joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
