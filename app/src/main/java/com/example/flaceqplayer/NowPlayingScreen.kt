package com.example.flaceqplayer

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // File picker launcher for FLAC audio files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.playFlacFile(context, it) }
    }

    // Dynamic background color from Cover Art via Palette API
    val defaultDark = Color(0xFF0F1016)
    var extractedColor by remember { mutableStateOf(defaultDark) }

    LaunchedEffect(state.coverBitmap) {
        val bitmap = state.coverBitmap
        if (bitmap != null) {
            Palette.from(bitmap).generate { palette ->
                val swatch = palette?.darkVibrantSwatch
                    ?: palette?.dominantSwatch
                    ?: palette?.mutedSwatch
                swatch?.rgb?.let { rgb ->
                    extractedColor = Color(rgb)
                }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgColor.copy(alpha = 0.85f),
                        Color(0xFF07080B)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("audio/*", "audio/flac")) }) {
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
                    color = Color.LightGray.copy(alpha = 0.8f)
                )

                IconButton(onClick = { viewModel.setEqSheetVisible(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Large Center Cover Art with Elevation Shadow & Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = animatedBgColor
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1B1D26)),
                contentAlignment = Alignment.Center
            ) {
                if (state.coverBitmap != null) {
                    Image(
                        bitmap = state.coverBitmap!!.asImageBitmap(),
                        contentDescription = "Album Cover Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Minimalist Dark Placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "No Artwork",
                            tint = Color(0xFF3E4254),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FLAC AUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF565B72),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            // Bottom Player Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Technical Specs Chip (Sample rate, Bit depth)
                Surface(
                    color = Color(0xFF1E202B).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = state.audioSpecs,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Title & Artist
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Progress Scrubber Slider
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
                        viewModel.seekTo(seekPos.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF282A36)
                    )
                )

                // Timestamp indicators
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

                Spacer(modifier = Modifier.height(8.dp))

                // Volume Slider Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.volume == 0f) Icons.Rounded.VolumeMute else Icons.Rounded.VolumeDown,
                        contentDescription = "Volume",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = state.volume,
                        onValueChange = { viewModel.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.LightGray,
                            activeTrackColor = Color.LightGray,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Playback Controls (Prev, Play/Pause, Next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skipPrevious() }) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Play/Pause with Bouncy Scale Animation
                    val playButtonScale by animateFloatAsState(
                        targetValue = if (state.isPlaying) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                        label = "playScale"
                    )

                    Surface(
                        onClick = { viewModel.togglePlayPause() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(playButtonScale)
                            .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.skipNext() }) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        // Equalizer Modal Bottom Sheet
        if (state.isEqOpen) {
            EqualizerBottomSheet(onDismiss = { viewModel.setEqSheetVisible(false) })
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
