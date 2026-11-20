package com.example.flaceqplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flaceqplayer.model.FlacTrack
import com.example.flaceqplayer.model.LyricsData
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TrackMetadataDialog(
    track: FlacTrack,
    onDismiss: () -> Unit
) {
    val sizeMb = if (track.fileSizeBytes > 0L) {
        String.format(Locale.US, "%.2f MB", track.fileSizeBytes / (1024f * 1024f))
    } else "-"

    val rows = listOf(
        "Judul" to track.title,
        "Artis" to track.artist,
        "Album" to track.album,
        "Tahun / Tanggal" to track.date.ifBlank { "-" },
        "Genre" to track.genre.ifBlank { "-" },
        "Track / Disc" to "${track.trackNumber.ifBlank { "-" }} / ${track.discNumber.ifBlank { "-" }}",
        "Spesifikasi" to track.technicalBadge,
        "Durasi" to formatDurationMs(track.durationMs),
        "Ukuran File" to sizeMb,
        "Cover Art" to if (track.hasCover) "Tersedia (${track.coverMime})" else "Tidak ada",
        "Nama File" to track.fileName,
        "Lokasi" to track.filePath.ifBlank { track.uri.toString() }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metadata File FLAC") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, fontSize = 11.sp, color = TextMuted)
                        Text(v, fontSize = 13.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
fun EditLyricsDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit / Tempel Lirik") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tempel lirik bahasa Jepang (teks biasa atau LRC [mm:ss.xx]). Romaji dibuat otomatis.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("[00:12.34] 碧い瞳の中に") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) { Text("Reset") }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = { onSave(text) }) { Text("Simpan") }
            }
        }
    )
}

@Composable
fun QuickRomajiConverterDialog(
    accentColor: Color,
    onConvert: suspend (String, String) -> LyricsData,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var translateLang by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LyricsData?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konverter Jepang → Romaji") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Tempel lirik Jepang di sini...\nContoh: 今日も明日も君へ歌うよ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = translateLang.isNotBlank(),
                        onClick = {
                            translateLang = when (translateLang) {
                                "" -> "id"
                                "id" -> "en"
                                else -> ""
                            }
                        },
                        label = {
                            Text(
                                when (translateLang) {
                                    "id" -> "Arti: ID"
                                    "en" -> "Arti: EN"
                                    else -> "Arti: Mati"
                                },
                                fontSize = 12.sp
                            )
                        }
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                scope.launch {
                                    isWorking = true
                                    result = onConvert(inputText, translateLang)
                                    isWorking = false
                                }
                            }
                        },
                        enabled = !isWorking && inputText.isNotBlank()
                    ) {
                        Text(if (isWorking) "Proses..." else "Konversi")
                    }
                }

                val data = result
                if (data != null && data.lines.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items(data.lines) { line ->
                            LyricLineCard(
                                line = line,
                                isActive = false,
                                isSynced = false,
                                showRomaji = true,
                                showFurigana = true,
                                romajiFirst = false,
                                showTranslation = translateLang.isNotBlank(),
                                accentColor = accentColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
