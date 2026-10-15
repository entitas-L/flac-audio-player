package com.example.flaceqplayer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

// ═══════════════════════════════════════════════════════════════════════════
// NowPlayingScreen – top-level screen
// Performance strategy for low-end phones:
//  • Background gradient Brush is re-created only when animatedBgColor changes
//    (remember(animatedBgColor)), not on every recomposition.
//  • Sub-composables (TopBar, CoverArtSection, ControlsSection) each read only
//    the state slices they need → Compose can skip them when unrelated state changes.
//  • No shadow() modifiers on inner elements (expensive GPU op); cover art uses
//    a lightweight border instead.
//  • Bitmap is pre-downscaled in MetadataExtractor before Palette runs.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // ── File picker ──────────────────────────────────────────────────────
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.playFlacFile(context, it) }
    }
    val openFilePicker: () -> Unit = remember { { filePicker.launch(arrayOf("audio/flac", "audio/*")) } }

    // ── Dynamic background color via Palette ─────────────────────────────
    val defaultDark = Color(0xFF0D0E14)
    var extractedColor by remember { mutableStateOf(defaultDark) }

    LaunchedEffect(state.coverBitmap) {
        val bmp = state.coverBitmap
        if (bmp != null) {
            // Bitmap is already ≤ 512px (downscaled in MetadataExtractor) → fast
            Palette.from(bmp).generate { palette ->
                val swatch = palette?.darkVibrantSwatch
                    ?: palette?.dominantSwatch
                    ?: palette?.mutedSwatch
                swatch?.rgb?.let { extractedColor = Color(it) }
            }
        } else {
            extractedColor = defaultDark
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = extractedColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicBg"
    )

    // Brush re-created only when color changes, not every frame
    val backgroundBrush = remember(animatedBgColor) {
        Brush.verticalGradient(
            colors = listOf(
                animatedBgColor.copy(alpha = 0.90f),
                Color(0xFF060709)
            )
        )
    }

    // ── Error Snackbar ───────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    // ── Root layout ──────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopBar(
                onOpenFile = openFilePicker,
                onOpenEq = { viewModel.setEqSheetVisible(true) }
            )

            CoverArtSection(
                coverBitmap = state.coverBitmap,
                accentColor = animatedBgColor
            )

            ControlsSection(
                state = state,
                onSeekTo = viewModel::seekTo,
                onTogglePlay = viewModel::togglePlayPause,
                onVolumeChange = viewModel::setVolume,
                onPrevious = viewModel::seekToStart,
                onNext = openFilePicker          // ← "next" opens the file picker
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Equalizer Bottom Sheet (rendered on top of everything)
        if (state.isEqOpen) {
            EqualizerBottomSheet(onDismiss = { viewModel.setEqSheetVisible(false) })
        }
    }
}

// ── Sub-composable: Top App Bar ──────────────────────────────────────────
@Composable
private fun TopBar(
    onOpenFile: () -> Unit,
    onOpenEq: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenFile) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Open FLAC File",
                tint = Color.White
            )
        }

        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp,
            color = Color.LightGray.copy(alpha = 0.7f)
        )

        IconButton(onClick = onOpenEq) {
            Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = "Equalizer",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Sub-composable: Cover Art ────────────────────────────────────────────
// Performance: no shadow() here. Uses a subtle border which is GPU-friendly.
@Composable
private fun CoverArtSection(
    coverBitmap: android.graphics.Bitmap?,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1C25)),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = "Album Cover Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            CoverArtPlaceholder()
        }
    }
}

@Composable
private fun CoverArtPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = "No Artwork",
            tint = Color(0xFF36394D),
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "FLAC HI-RES",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4A4E63),
            letterSpacing = 2.sp
        )
    }
}

// ── Sub-composable: Controls (specs, title, seek, volume, buttons) ────────
@Composable
private fun ControlsSection(
    state: PlayerUiState,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Audio specs badge ──
        if (state.audioSpecs.isNotEmpty()) {
            Surface(
                color = Color(0xFF1C1E2A).copy(alpha = 0.85f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = state.audioSpecs,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Track title & artist ──
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (state.album.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.album,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Seek bar ──
        SeekBar(state = state, onSeekTo = onSeekTo)

        Spacer(Modifier.height(8.dp))

        // ── Volume slider ──
        VolumeRow(volume = state.volume, onVolumeChange = onVolumeChange)

        Spacer(Modifier.height(16.dp))

        // ── Playback buttons ──
        PlaybackControls(
            isPlaying = state.isPlaying,
            onPrevious = onPrevious,
            onTogglePlay = onTogglePlay,
            onNext = onNext
        )
    }
}

@Composable
private fun SeekBar(
    state: PlayerUiState,
    onSeekTo: (Long) -> Unit
) {
    // Local seeking state prevents slider from snapping back during drag
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val displayPos = if (isSeeking) seekPos.toLong() else state.currentPositionMs

    Slider(
        value = displayPos.toFloat().coerceIn(0f, duration.toFloat()),
        onValueChange = {
            isSeeking = true
            seekPos = it
        },
        onValueChangeFinished = {
            onSeekTo(seekPos.toLong())
            isSeeking = false
        },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color(0xFF26293A)
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatMillis(displayPos),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = formatMillis(duration),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun VolumeRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (volume == 0f) Icons.Rounded.VolumeMute else Icons.Rounded.VolumeDown,
            contentDescription = "Volume",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF9E9EB8),
                activeTrackColor = Color(0xFF9E9EB8),
                inactiveTrackColor = Color(0xFF232530)
            )
        )
        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = "Max Volume",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SkipPrevious → seeks to beginning of current track
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Restart Track",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play / Pause – bouncy scale animation
        val scale by animateFloatAsState(
            targetValue = if (isPlaying) 1.07f else 1.0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
            label = "playScale"
        )

        Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(70.dp)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // SkipNext → repurposed as "Open file picker" (single-track player)
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = "Open Another FLAC",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ── Helper ────────────────────────────────────────────────────────────────
private fun formatMillis(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
