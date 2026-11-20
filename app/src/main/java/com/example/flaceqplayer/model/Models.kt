package com.example.flaceqplayer.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.Locale

enum class AppTab {
    PLAYER,
    LYRICS,
    LIBRARY
}

enum class LibraryFilter {
    ALL,
    ALBUMS,
    WITH_LYRICS
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class LyricsSource(val label: String) {
    EMBEDDED("Metadata FLAC"),
    SIDECAR_LRC("File .lrc"),
    LRCLIB("LRCLIB Online"),
    MANUAL("Lirik Manual"),
    NONE("Belum Ada Lirik")
}

data class FlacTrack(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val filePath: String = "",
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val date: String = "",
    val genre: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val bitrateKbps: Int = 0,
    val hasCover: Boolean = false,
    val coverMime: String = "",
    val embeddedLyrics: String = ""
) {
    val hasEmbeddedLyrics: Boolean get() = embeddedLyrics.isNotBlank()

    val technicalBadge: String
        get() {
            val parts = mutableListOf("FLAC")
            if (bitDepth > 0 && sampleRateHz > 0) {
                val khz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000} kHz"
                } else {
                    String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000f)
                }
                parts.add("$bitDepth-bit / $khz")
            }
            if (bitrateKbps > 0) {
                parts.add("$bitrateKbps kbps")
            }
            parts.add(if (channels <= 1) "Mono" else "Stereo")
            return parts.joinToString(" • ")
        }
}

data class LyricSegment(
    val surface: String,
    val readingHiragana: String,
    val romaji: String,
    val hasKanji: Boolean
)

data class LyricLine(
    val index: Int,
    val timeMs: Long?,
    val original: String,
    val romaji: String,
    val hiragana: String,
    val segments: List<LyricSegment>,
    val isJapanese: Boolean,
    val translation: String = ""
)

data class LyricsData(
    val source: LyricsSource = LyricsSource.NONE,
    val synced: Boolean = false,
    val rawText: String = "",
    val lines: List<LyricLine> = emptyList(),
    val translationLang: String = "",
    val translationError: String = ""
) {
    val hasJapanese: Boolean get() = lines.any { it.isJapanese }
}

data class PlayerUiState(
    val activeTab: AppTab = AppTab.PLAYER,
    val tracks: List<FlacTrack> = emptyList(),
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val currentTrack: FlacTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val decodedAudioBadge: String = "",
    val coverBitmap: Bitmap? = null,
    val accentColor: Color = Color(0xFF7C6CFF),
    val glowColor: Color = Color(0xFF3E3591),
    val lyrics: LyricsData = LyricsData(),
    val lyricsLoading: Boolean = false,
    val activeLyricIndex: Int = -1,
    val showRomaji: Boolean = true,
    val showFurigana: Boolean = true,
    val romajiFirst: Boolean = false,
    val autoFetchOnline: Boolean = true,
    val translateLang: String = "",
    val dictionaryReady: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val statusBanner: String? = null
)
