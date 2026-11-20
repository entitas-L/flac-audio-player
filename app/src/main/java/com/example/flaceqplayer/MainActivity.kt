package com.example.flaceqplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import com.example.flaceqplayer.model.AppTab
import com.example.flaceqplayer.ui.*

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            playerViewModel.scanLibrary()
        }
    }

    private val pickFlacLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            playerViewModel.handlePickedUri(uri, autoPlay = true)
            playerViewModel.switchTab(AppTab.PLAYER)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        playerViewModel.initController(this)
        handleIncomingIntent(intent)

        setContent {
            val state by playerViewModel.uiState.collectAsState()
            val filteredTracks = remember(state.tracks, state.searchQuery, state.filter) {
                playerViewModel.filteredTracks()
            }

            var showEqSheet by remember { mutableStateOf(false) }
            var showInfoDialog by remember { mutableStateOf(false) }
            var showEditLyricsDialog by remember { mutableStateOf(false) }
            var showConverterDialog by remember { mutableStateOf(false) }

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(state.statusBanner) {
                val msg = state.statusBanner
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                    playerViewModel.clearStatusBanner()
                }
            }

            FlacPlayerTheme(accent = state.accentColor) {
                Scaffold(
                    containerColor = BgDark,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        Column {
                            val track = state.currentTrack
                            if (track != null && state.activeTab != AppTab.PLAYER) {
                                Surface(
                                    color = SurfaceElevated,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { playerViewModel.switchTab(AppTab.PLAYER) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SurfaceDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (state.coverBitmap != null) {
                                                Image(
                                                    bitmap = state.coverBitmap!!.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TextMuted)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                                            Icon(
                                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = "Putar / Jeda",
                                                tint = TextPrimary
                                            )
                                        }
                                        IconButton(onClick = { playerViewModel.playNext() }) {
                                            Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = TextPrimary)
                                        }
                                    }
                                }
                            }

                            NavigationBar(containerColor = Color(0xFF0E111B)) {
                                NavigationBarItem(
                                    selected = state.activeTab == AppTab.PLAYER,
                                    onClick = { playerViewModel.switchTab(AppTab.PLAYER) },
                                    icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                                    label = { Text("Putar") }
                                )
                                NavigationBarItem(
                                    selected = state.activeTab == AppTab.LYRICS,
                                    onClick = { playerViewModel.switchTab(AppTab.LYRICS) },
                                    icon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
                                    label = { Text("Lirik Romaji") }
                                )
                                NavigationBarItem(
                                    selected = state.activeTab == AppTab.LIBRARY,
                                    onClick = { playerViewModel.switchTab(AppTab.LIBRARY) },
                                    icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                                    label = { Text("Pustaka") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (state.activeTab) {
                            AppTab.PLAYER -> NowPlayingScreen(
                                state = state,
                                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                onPrev = { playerViewModel.playPrevious() },
                                onNext = { playerViewModel.playNext() },
                                onSeek = { playerViewModel.seekTo(it) },
                                onToggleShuffle = { playerViewModel.toggleShuffle() },
                                onCycleRepeat = { playerViewModel.cycleRepeat() },
                                onOpenEq = { showEqSheet = true },
                                onOpenInfo = { showInfoDialog = true },
                                onOpenLyricsTab = { playerViewModel.switchTab(AppTab.LYRICS) },
                                onOpenLibraryTab = { playerViewModel.switchTab(AppTab.LIBRARY) }
                            )
                            AppTab.LYRICS -> LyricsScreen(
                                state = state,
                                onToggleRomaji = { playerViewModel.setShowRomaji(it) },
                                onToggleFurigana = { playerViewModel.setShowFurigana(it) },
                                onToggleRomajiFirst = { playerViewModel.setRomajiFirst(it) },
                                onToggleAutoFetch = { playerViewModel.setAutoFetchOnline(it) },
                                onChangeTranslateLang = { playerViewModel.setTranslateLang(it) },
                                onFetchOnline = { playerViewModel.fetchOnlineLyricsNow() },
                                onOpenEditLyrics = { showEditLyricsDialog = true },
                                onSeekToMs = { playerViewModel.seekTo(it) }
                            )
                            AppTab.LIBRARY -> LibraryScreen(
                                state = state,
                                filteredTracks = filteredTracks,
                                loadThumbnail = { playerViewModel.loadThumbnail(it) },
                                onSearchChange = { playerViewModel.setSearchQuery(it) },
                                onFilterChange = { playerViewModel.setFilter(it) },
                                onRescan = { playerViewModel.scanLibrary() },
                                onPickFlacFile = { pickFlacLauncher.launch(arrayOf("audio/flac", "audio/*", "*/*")) },
                                onOpenConverter = { showConverterDialog = true },
                                onSelectTrack = { track ->
                                    playerViewModel.playTrack(track)
                                    playerViewModel.switchTab(AppTab.PLAYER)
                                }
                            )
                        }
                    }
                }

                if (showEqSheet) {
                    EqualizerBottomSheet(
                        isEnabled = state.eqEnabled,
                        activePreset = state.eqPreset,
                        onToggleEnabled = { playerViewModel.setEqEnabled(it) },
                        onSelectPreset = { playerViewModel.applyEqPreset(it) },
                        onManualBandChange = { playerViewModel.markEqCustom() },
                        onDismiss = { showEqSheet = false }
                    )
                }

                if (showInfoDialog) {
                    state.currentTrack?.let { current ->
                        TrackMetadataDialog(
                            track = current,
                            onDismiss = { showInfoDialog = false }
                        )
                    }
                }

                if (showEditLyricsDialog && state.currentTrack != null) {
                    EditLyricsDialog(
                        initialText = state.lyrics.rawText,
                        onSave = { raw ->
                            playerViewModel.saveManualLyrics(raw)
                            showEditLyricsDialog = false
                        },
                        onReset = {
                            playerViewModel.saveManualLyrics("")
                            showEditLyricsDialog = false
                        },
                        onDismiss = { showEditLyricsDialog = false }
                    )
                }

                if (showConverterDialog) {
                    QuickRomajiConverterDialog(
                        accentColor = state.accentColor,
                        onConvert = { text, lang -> playerViewModel.convertStandaloneText(text, lang) },
                        onDismiss = { showConverterDialog = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                playerViewModel.handlePickedUri(uri, autoPlay = true)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
