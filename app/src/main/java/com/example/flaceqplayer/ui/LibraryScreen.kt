package com.example.flaceqplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flaceqplayer.model.FlacTrack
import com.example.flaceqplayer.model.LibraryFilter
import com.example.flaceqplayer.model.PlayerUiState
import java.util.Locale

@Composable
fun LibraryScreen(
    state: PlayerUiState,
    filteredTracks: List<FlacTrack>,
    loadThumbnail: suspend (FlacTrack) -> Bitmap?,
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onRescan: () -> Unit,
    onPickFlacFile: () -> Unit,
    onOpenConverter: () -> Unit,
    onSelectTrack: (FlacTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Surface(color = SurfaceDark) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pustaka FLAC", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${state.tracks.size} lagu ditemukan", color = TextMuted, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onOpenConverter) {
                            Icon(Icons.Rounded.Translate, contentDescription = "Konverter Romaji Cepat", tint = state.accentColor)
                        }
                        IconButton(onClick = onPickFlacFile) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Pilih File FLAC", tint = TextPrimary)
                        }
                        IconButton(onClick = onRescan) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Scan Ulang", tint = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Cari judul, artis, album...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter == LibraryFilter.ALL,
                        onClick = { onFilterChange(LibraryFilter.ALL) },
                        label = { Text("Semua") }
                    )
                    FilterChip(
                        selected = state.filter == LibraryFilter.ALBUMS,
                        onClick = { onFilterChange(LibraryFilter.ALBUMS) },
                        label = { Text("Per Album") }
                    )
                    FilterChip(
                        selected = state.filter == LibraryFilter.WITH_LYRICS,
                        onClick = { onFilterChange(LibraryFilter.WITH_LYRICS) },
                        label = { Text("Ada Lirik") }
                    )
                }
            }
        }

        if (state.isScanning && filteredTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = state.accentColor)
                    Spacer(Modifier.height(10.dp))
                    Text("Memindai file FLAC di perangkat...", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.tracks.isEmpty()) {
                            "Belum ada file .flac yang terbaca.\nPastikan izin Audio diberikan, atau tekan tombol di bawah untuk memilih file .flac secara langsung."
                        } else {
                            "Tidak ada lagu yang cocok dengan pencarian."
                        },
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onPickFlacFile,
                        colors = ButtonDefaults.buttonColors(containerColor = state.accentColor)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih File FLAC")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.filter == LibraryFilter.ALBUMS) {
                    val grouped = filteredTracks.groupBy { it.album }
                    grouped.forEach { (albumName, albumTracks) ->
                        item(key = "hdr_$albumName") {
                            Text(
                                text = albumName.uppercase(Locale.ROOT),
                                color = state.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }
                        items(albumTracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = state.currentTrack?.id == track.id,
                                accentColor = state.accentColor,
                                loadThumbnail = loadThumbnail,
                                onClick = { onSelectTrack(track) }
                            )
                        }
                    }
                } else {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = state.currentTrack?.id == track.id,
                            accentColor = state.accentColor,
                            loadThumbnail = loadThumbnail,
                            onClick = { onSelectTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: FlacTrack,
    isPlaying: Boolean,
    accentColor: Color,
    loadThumbnail: suspend (FlacTrack) -> Bitmap?,
    onClick: () -> Unit
) {
    var thumb by remember(track.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track.id) {
        if (track.hasCover) {
            thumb = loadThumbnail(track)
        }
    }

    val bg = if (isPlaying) accentColor.copy(alpha = 0.2f) else Color.Transparent
    val border = if (isPlaying) accentColor.copy(alpha = 0.45f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TextMuted)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) accentColor else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDurationMs(track.durationMs),
                color = TextMuted,
                fontSize = 12.sp
            )
            if (track.hasEmbeddedLyrics || track.bitDepth > 0) {
                val tag = if (track.hasEmbeddedLyrics) "Lirik" else "${track.bitDepth}b/${track.sampleRateHz / 1000}k"
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = tag,
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
