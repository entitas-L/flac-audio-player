package com.example.flaceqplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flaceqplayer.model.LyricLine
import com.example.flaceqplayer.model.PlayerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineCard(
    line: LyricLine,
    isActive: Boolean,
    isSynced: Boolean,
    showRomaji: Boolean,
    showFurigana: Boolean,
    romajiFirst: Boolean,
    showTranslation: Boolean,
    accentColor: Color,
    onClick: (() -> Unit)? = null
) {
    val bgColor = if (isActive) accentColor.copy(alpha = 0.22f) else Color.Transparent
    val borderColor = if (isActive) accentColor.copy(alpha = 0.55f) else Color.Transparent
    val textAlpha = if (!isSynced || isActive) 1f else 0.65f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .let { mod -> if (onClick != null) mod.clickable { onClick() } else mod }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val originalBlock: @Composable () -> Unit = {
            if (line.original.isBlank()) {
                Text("♪", color = TextPrimary.copy(alpha = textAlpha), fontSize = 16.sp)
            } else if (showFurigana && line.isJapanese && line.segments.any { it.hasKanji && it.readingHiragana.isNotBlank() }) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSynced && line.timeMs != null) {
                        Text(
                            text = formatDurationMs(line.timeMs),
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.Bottom)
                                .padding(end = 6.dp, bottom = 2.dp)
                        )
                    }
                    line.segments.forEach { seg ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (seg.hasKanji && seg.readingHiragana.isNotBlank()) {
                                Text(
                                    text = seg.readingHiragana,
                                    color = FuriganaTint.copy(alpha = textAlpha),
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp
                                )
                            } else {
                                Text(
                                    text = "",
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp
                                )
                            }
                            Text(
                                text = seg.surface,
                                color = if (romajiFirst) TextMuted.copy(alpha = textAlpha) else TextPrimary.copy(alpha = textAlpha),
                                fontSize = if (romajiFirst) 14.sp else 17.sp,
                                fontWeight = if (romajiFirst) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                val prefix = if (isSynced && line.timeMs != null) "${formatDurationMs(line.timeMs)}  " else ""
                Text(
                    text = prefix + line.original,
                    color = if (romajiFirst) TextMuted.copy(alpha = textAlpha) else TextPrimary.copy(alpha = textAlpha),
                    fontSize = if (romajiFirst) 14.sp else 17.sp,
                    fontWeight = if (romajiFirst) FontWeight.Normal else FontWeight.SemiBold
                )
            }
        }

        val romajiBlock: @Composable () -> Unit = {
            if (showRomaji && line.isJapanese && line.romaji.isNotBlank()) {
                Text(
                    text = line.romaji,
                    color = if (romajiFirst) TextPrimary.copy(alpha = textAlpha) else RomajiBlue.copy(alpha = textAlpha),
                    fontSize = if (romajiFirst) 17.sp else 14.sp,
                    fontWeight = if (romajiFirst) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
        }

        if (romajiFirst && showRomaji && line.isJapanese && line.romaji.isNotBlank()) {
            romajiBlock()
            Spacer(modifier = Modifier.height(4.dp))
            originalBlock()
        } else {
            originalBlock()
            if (showRomaji && line.isJapanese && line.romaji.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                romajiBlock()
            }
        }

        if (showTranslation && line.translation.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.translation,
                color = TranslationMint.copy(alpha = textAlpha),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun LyricsScreen(
    state: PlayerUiState,
    onToggleRomaji: (Boolean) -> Unit,
    onToggleFurigana: (Boolean) -> Unit,
    onToggleRomajiFirst: (Boolean) -> Unit,
    onToggleAutoFetch: (Boolean) -> Unit,
    onChangeTranslateLang: (String) -> Unit,
    onFetchOnline: () -> Unit,
    onOpenEditLyrics: () -> Unit,
    onSeekToMs: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val lyrics = state.lyrics

    LaunchedEffect(state.activeLyricIndex, lyrics.lines.size) {
        if (state.activeLyricIndex in lyrics.lines.indices) {
            listState.animateScrollToItem(
                index = (state.activeLyricIndex - 2).coerceAtLeast(0)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Header & Actions
        Surface(color = SurfaceDark) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentTrack?.title ?: "Lirik & Romaji",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${lyrics.source.label}${if (lyrics.synced) " • Sync" else ""}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onFetchOnline,
                            enabled = state.currentTrack != null && !state.lyricsLoading,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cari", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenEditLyrics,
                            enabled = state.currentTrack != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.showRomaji,
                        onClick = { onToggleRomaji(!state.showRomaji) },
                        label = { Text("Romaji", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = state.showFurigana,
                        onClick = { onToggleFurigana(!state.showFurigana) },
                        label = { Text("Furigana", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = state.romajiFirst,
                        onClick = { onToggleRomajiFirst(!state.romajiFirst) },
                        label = { Text("Fokus Romaji", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = state.translateLang.isNotBlank(),
                        onClick = {
                            val next = when (state.translateLang) {
                                "" -> "id"
                                "id" -> "en"
                                else -> ""
                            }
                            onChangeTranslateLang(next)
                        },
                        label = {
                            val lbl = when (state.translateLang) {
                                "id" -> "Arti: ID"
                                "en" -> "Arti: EN"
                                else -> "Arti: Mati"
                            }
                            Text(lbl, fontSize = 12.sp)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = state.autoFetchOnline,
                        onClick = { onToggleAutoFetch(!state.autoFetchOnline) },
                        label = { Text("Auto LRCLIB", fontSize = 12.sp) }
                    )
                }
            }
        }

        // Body
        if (state.lyricsLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = state.accentColor)
                    Spacer(Modifier.height(10.dp))
                    Text("Mengonversi lirik Jepang ke Romaji...", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else if (lyrics.lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.currentTrack == null) {
                            "Pilih lagu di Pustaka untuk menampilkan lirik beserta bacaan Romaji otomatis."
                        } else {
                            "Lirik belum tersedia di metadata file FLAC ini.\nKetuk 'Cari' untuk mencari lirik otomatis dari LRCLIB atau 'Edit' untuk menempel lirik manual."
                        },
                        color = TextMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (state.currentTrack != null) {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onFetchOnline,
                            colors = ButtonDefaults.buttonColors(containerColor = state.accentColor)
                        ) {
                            Icon(Icons.Rounded.Public, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cari Lirik Online (LRCLIB)")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lyrics.lines, key = { idx, item -> "${idx}_${item.timeMs}" }) { idx, line ->
                    LyricLineCard(
                        line = line,
                        isActive = lyrics.synced && idx == state.activeLyricIndex,
                        isSynced = lyrics.synced,
                        showRomaji = state.showRomaji,
                        showFurigana = state.showFurigana,
                        romajiFirst = state.romajiFirst,
                        showTranslation = state.translateLang.isNotBlank(),
                        accentColor = state.accentColor,
                        onClick = if (lyrics.synced && line.timeMs != null) {
                            { onSeekToMs(line.timeMs) }
                        } else null
                    )
                }
            }
        }
    }
}
