package com.example.flaceqplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flaceqplayer.model.PlayerUiState
import com.example.flaceqplayer.model.RepeatMode

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenLyricsTab: () -> Unit,
    onOpenLibraryTab: () -> Unit
) {
    val animatedGlow by animateColorAsState(targetValue = state.glowColor, label = "glow")
    val animatedAccent by animateColorAsState(targetValue = state.accentColor, label = "accent")

    val backgroundBrush = remember(animatedGlow) {
        Brush.verticalGradient(
            colors = listOf(
                animatedGlow.copy(alpha = 0.55f),
                BgDark,
                BgDark
            )
        )
    }

    val track = state.currentTrack
    val scroll = rememberScrollState()

    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val displayPos = if (isDragging) dragPositionMs.toLong() else state.positionMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cover Art
            Box(
                modifier = Modifier
                    .size(276.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = animatedAccent, spotColor = animatedAccent)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (state.coverBitmap != null) {
                    Image(
                        bitmap = state.coverBitmap.asImageBitmap(),
                        contentDescription = "Album Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("FLAC", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (track == null) {
                Text(
                    text = "Pilih Lagu FLAC",
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Buka tab Pustaka untuk memutar file .flac beserta cover art & Romaji otomatis.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenLibraryTab,
                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Pustaka (${state.tracks.size} lagu)")
                }
            } else {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    color = TextMuted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (track.date.isNotBlank()) "${track.album} (${track.date})" else track.album,
                    color = TextMuted.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Lossless Audio Spec Badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = animatedAccent.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, animatedAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = state.decodedAudioBadge.ifBlank { track.technicalBadge },
                        color = Color(0xFFE9ECFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seekbar
            Slider(
                value = displayPos.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = {
                    isDragging = true
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(dragPositionMs.toLong())
                },
                valueRange = 0f..duration.toFloat(),
                enabled = track != null,
                colors = SliderDefaults.colors(
                    thumbColor = animatedAccent,
                    activeTrackColor = animatedAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDurationMs(displayPos), color = TextMuted, fontSize = 12.sp)
                Text(formatDurationMs(state.durationMs), color = TextMuted, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) animatedAccent else TextMuted
                    )
                }
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = TextPrimary)
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(animatedAccent)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Putar / Jeda",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = TextPrimary)
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Ulangi",
                        tint = if (state.repeatMode != RepeatMode.OFF) animatedAccent else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // EQ & Info Quick Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onOpenEq,
                    label = { Text(if (state.eqEnabled) "EQ: ${state.eqPreset}" else "Equalizer") },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = onOpenInfo,
                    enabled = track != null,
                    label = { Text("Metadata FLAC") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inline Mini Romaji Lyrics Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLyricsTab() }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lirik & Romaji", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Buka Penuh ↗", color = animatedAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val lines = state.lyrics.lines
                    if (state.lyricsLoading) {
                        Text("Memproses lirik & Romaji...", color = TextMuted, fontSize = 13.sp)
                    } else if (lines.isEmpty()) {
                        Text(
                            text = if (track == null) "Pilih lagu untuk melihat lirik dan Romaji otomatis." else "Belum ada lirik. Ketuk untuk cari online atau tempel manual.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        val idx = if (state.activeLyricIndex >= 0) state.activeLyricIndex else 0
                        val preview = lines.drop(idx).take(2)
                        preview.forEachIndexed { offset, line ->
                            if (offset > 0) Spacer(modifier = Modifier.height(8.dp))
                            val alpha = if (offset == 0) 1f else 0.55f
                            Text(
                                text = line.original.ifBlank { "♪" },
                                color = TextPrimary.copy(alpha = alpha),
                                fontSize = 15.sp,
                                fontWeight = if (offset == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (state.showRomaji && line.isJapanese && line.romaji.isNotBlank()) {
                                Text(
                                    text = line.romaji,
                                    color = RomajiBlue.copy(alpha = alpha),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (state.translateLang.isNotBlank() && line.translation.isNotBlank()) {
                                Text(
                                    text = line.translation,
                                    color = TranslationMint.copy(alpha = alpha),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
