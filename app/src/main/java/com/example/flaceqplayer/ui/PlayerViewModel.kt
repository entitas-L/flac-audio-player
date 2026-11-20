package com.example.flaceqplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.example.flaceqplayer.audio.EqPreset
import com.example.flaceqplayer.audio.EqualizerHelper
import com.example.flaceqplayer.audio.FlacTagReader
import com.example.flaceqplayer.audio.PlaybackService
import com.example.flaceqplayer.data.FlacLibraryScanner
import com.example.flaceqplayer.lyrics.LyricsRepository
import com.example.flaceqplayer.lyrics.RomajiConverter
import com.example.flaceqplayer.model.AppTab
import com.example.flaceqplayer.model.FlacTrack
import com.example.flaceqplayer.model.LibraryFilter
import com.example.flaceqplayer.model.LyricsData
import com.example.flaceqplayer.model.LyricsSource
import com.example.flaceqplayer.model.PlayerUiState
import com.example.flaceqplayer.model.RepeatMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("flac_audio_player_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            showRomaji = prefs.getBoolean("showRomaji", true),
            showFurigana = prefs.getBoolean("showFurigana", true),
            romajiFirst = prefs.getBoolean("romajiFirst", false),
            autoFetchOnline = prefs.getBoolean("autoFetchOnline", true),
            translateLang = prefs.getString("translateLang", "") ?: "",
            eqEnabled = EqualizerHelper.isEnabled
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var tickerJob: Job? = null
    private var lyricsJob: Job? = null
    private val attemptedAutoFetchIds = mutableSetOf<String>()
    private val pickedTracks = mutableListOf<FlacTrack>()
    private val thumbnailCache = LinkedHashMap<String, Bitmap?>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    decodedAudioBadge = PlaybackService.latestAudioSpecs.ifEmpty { it.decodedAudioBadge }
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = mediaController ?: return
            if (playbackState == Player.STATE_READY) {
                _uiState.update {
                    it.copy(
                        durationMs = ctrl.duration.coerceAtLeast(it.currentTrack?.durationMs ?: 0L),
                        decodedAudioBadge = PlaybackService.latestAudioSpecs.ifEmpty { it.decodedAudioBadge }
                    )
                }
            } else if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            RomajiConverter.ensureLoaded(getApplication())
            _uiState.update { it.copy(dictionaryReady = RomajiConverter.isReady()) }
        }
        scanLibrary()
    }

    fun initController(context: Context) {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val ctrl = future.get()
                mediaController = ctrl
                ctrl.addListener(playerListener)
                startPositionTicker()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val ctrl = mediaController
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0L } ?: _uiState.value.currentTrack?.durationMs ?: 0L
                    val activeIdx = computeActiveLyricIndex(_uiState.value.lyrics, pos)
                    _uiState.update { state ->
                        state.copy(
                            positionMs = pos,
                            durationMs = dur,
                            activeLyricIndex = activeIdx,
                            decodedAudioBadge = PlaybackService.latestAudioSpecs.ifEmpty { state.decodedAudioBadge }
                        )
                    }
                }
                delay(200L)
            }
        }
    }

    private fun computeActiveLyricIndex(lyrics: LyricsData, positionMs: Long): Int {
        if (!lyrics.synced || lyrics.lines.isEmpty()) return -1
        val target = positionMs + 150L
        var active = -1
        for (i in lyrics.lines.indices) {
            val t = lyrics.lines[i].timeMs ?: continue
            if (t <= target) active = i else break
        }
        return active
    }

    fun switchTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val scanned = withContext(Dispatchers.IO) {
                FlacLibraryScanner.scanDeviceFlac(getApplication())
            }
            val merged = (pickedTracks + scanned).distinctBy { it.id }
            _uiState.update { it.copy(tracks = merged, isScanning = false) }
        }
    }

    fun handlePickedUri(uri: Uri, autoPlay: Boolean = true) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val track = withContext(Dispatchers.IO) {
                FlacLibraryScanner.readPickedFlac(app, uri)
            }
            if (track == null) {
                _uiState.update { it.copy(statusBanner = "File yang dipilih bukan FLAC yang valid.") }
                return@launch
            }
            pickedTracks.removeAll { it.id == track.id }
            pickedTracks.add(0, track)
            val updated = (listOf(track) + _uiState.value.tracks).distinctBy { it.id }
            _uiState.update { it.copy(tracks = updated) }
            if (autoPlay) {
                playTrack(track)
            }
        }
    }

    suspend fun loadThumbnail(track: FlacTrack): Bitmap? {
        if (!track.hasCover) return null
        thumbnailCache[track.id]?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            FlacLibraryScanner.getOrCacheThumbnail(getApplication(), track)
        }
        if (thumbnailCache.size >= 80) {
            val first = thumbnailCache.keys.firstOrNull()
            if (first != null) thumbnailCache.remove(first)
        }
        thumbnailCache[track.id] = bmp
        return bmp
    }

    fun playTrack(track: FlacTrack) {
        PlaybackService.latestAudioSpecs = ""
        _uiState.update {
            it.copy(
                currentTrack = track,
                coverBitmap = null,
                positionMs = 0L,
                durationMs = track.durationMs,
                decodedAudioBadge = track.technicalBadge,
                activeLyricIndex = -1
            )
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            val coverBmp = withContext(Dispatchers.IO) {
                FlacTagReader.extractCoverBitmap(app, track.uri, maxSizePx = 720)
            }
            if (_uiState.value.currentTrack?.id != track.id) return@launch

            val (accent, glow) = withContext(Dispatchers.Default) {
                extractPaletteColors(coverBmp)
            }
            if (_uiState.value.currentTrack?.id != track.id) return@launch

            _uiState.update {
                it.copy(
                    coverBitmap = coverBmp,
                    accentColor = accent,
                    glowColor = glow
                )
            }

            val artBytes = withContext(Dispatchers.IO) {
                coverBmp?.let { bmp ->
                    ByteArrayOutputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                }
            }
            if (_uiState.value.currentTrack?.id != track.id) return@launch

            val metaBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
            if (artBytes != null && artBytes.isNotEmpty()) {
                metaBuilder.setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            val item = MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id)
                .setMediaMetadata(metaBuilder.build())
                .build()

            mediaController?.let { ctrl ->
                ctrl.setMediaItem(item)
                ctrl.prepare()
                ctrl.play()
            }
        }

        loadLyricsForTrack(track)
    }

    private fun extractPaletteColors(bitmap: Bitmap?): Pair<Color, Color> {
        val defaultAccent = Color(0xFF7C6CFF)
        val defaultGlow = Color(0xFF3E3591)
        if (bitmap == null) return defaultAccent to defaultGlow
        return try {
            val palette = Palette.from(bitmap).generate()
            val vibrant = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
            val dark = palette.darkVibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: vibrant
            val accent = if (vibrant != null) Color(vibrant) else defaultAccent
            val glow = if (dark != null) Color(dark) else defaultGlow
            accent to glow
        } catch (_: Exception) {
            defaultAccent to defaultGlow
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (_uiState.value.currentTrack == null) {
            val first = filteredTracks().firstOrNull() ?: _uiState.value.tracks.firstOrNull()
            if (first != null) playTrack(first)
            return
        }
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.seekTo(0L)
            }
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val ctrl = mediaController ?: return
        ctrl.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update {
            it.copy(
                positionMs = positionMs,
                activeLyricIndex = computeActiveLyricIndex(it.lyrics, positionMs)
            )
        }
    }

    fun playNext() {
        val pool = filteredTracks().ifEmpty { _uiState.value.tracks }
        if (pool.isEmpty()) return
        val current = _uiState.value.currentTrack
        val next = if (_uiState.value.shuffle && pool.size > 1) {
            pool.filter { it.id != current?.id }.random()
        } else if (current == null) {
            pool.first()
        } else {
            val idx = pool.indexOfFirst { it.id == current.id }
            pool[(idx + 1).mod(pool.size)]
        }
        playTrack(next)
    }

    fun playPrevious() {
        val ctrl = mediaController
        if (ctrl != null && ctrl.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val pool = filteredTracks().ifEmpty { _uiState.value.tracks }
        if (pool.isEmpty()) return
        val current = _uiState.value.currentTrack
        val prev = if (_uiState.value.shuffle && pool.size > 1) {
            pool.filter { it.id != current?.id }.random()
        } else if (current == null) {
            pool.first()
        } else {
            val idx = pool.indexOfFirst { it.id == current.id }
            pool[(idx - 1).mod(pool.size)]
        }
        playTrack(prev)
    }

    private fun handleTrackEnded() {
        val state = _uiState.value
        if (state.repeatMode == RepeatMode.ONE) {
            mediaController?.let {
                it.seekTo(0L)
                it.play()
            }
            return
        }
        val pool = filteredTracks().ifEmpty { state.tracks }
        if (pool.isEmpty()) return
        val idx = pool.indexOfFirst { it.id == state.currentTrack?.id }
        if (state.repeatMode == RepeatMode.OFF && !state.shuffle && idx == pool.lastIndex) {
            _uiState.update { it.copy(isPlaying = false) }
            return
        }
        playNext()
    }

    fun toggleShuffle() {
        _uiState.update {
            val next = !it.shuffle
            it.copy(
                shuffle = next,
                statusBanner = if (next) "Mode acak aktif" else "Mode acak mati"
            )
        }
    }

    fun cycleRepeat() {
        _uiState.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            val msg = when (next) {
                RepeatMode.OFF -> "Ulangi mati"
                RepeatMode.ALL -> "Ulangi semua lagu"
                RepeatMode.ONE -> "Ulangi 1 lagu"
            }
            it.copy(repeatMode = next, statusBanner = msg)
        }
    }

    fun filteredTracks(): List<FlacTrack> {
        val s = _uiState.value
        val q = s.searchQuery.trim().lowercase(Locale.ROOT)
        return s.tracks.filter { t ->
            if (s.filter == LibraryFilter.WITH_LYRICS && !t.hasEmbeddedLyrics) return@filter false
            if (q.isEmpty()) return@filter true
            t.title.lowercase(Locale.ROOT).contains(q) ||
                t.artist.lowercase(Locale.ROOT).contains(q) ||
                t.album.lowercase(Locale.ROOT).contains(q) ||
                t.fileName.lowercase(Locale.ROOT).contains(q)
        }
    }

    // --- Lyrics & Romaji Settings ---
    fun setShowRomaji(enabled: Boolean) {
        prefs.edit().putBoolean("showRomaji", enabled).apply()
        _uiState.update { it.copy(showRomaji = enabled) }
    }

    fun setShowFurigana(enabled: Boolean) {
        prefs.edit().putBoolean("showFurigana", enabled).apply()
        _uiState.update { it.copy(showFurigana = enabled) }
    }

    fun setRomajiFirst(enabled: Boolean) {
        prefs.edit().putBoolean("romajiFirst", enabled).apply()
        _uiState.update { it.copy(romajiFirst = enabled) }
    }

    fun setAutoFetchOnline(enabled: Boolean) {
        prefs.edit().putBoolean("autoFetchOnline", enabled).apply()
        _uiState.update { it.copy(autoFetchOnline = enabled) }
    }

    fun setTranslateLang(lang: String) {
        prefs.edit().putString("translateLang", lang).apply()
        _uiState.update { it.copy(translateLang = lang) }
        _uiState.value.currentTrack?.let { loadLyricsForTrack(it, allowAutoFetch = false) }
    }

    fun loadLyricsForTrack(track: FlacTrack, allowAutoFetch: Boolean = true) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(lyricsLoading = true) }
            val state = _uiState.value
            val shouldAutoFetch = allowAutoFetch &&
                state.autoFetchOnline &&
                attemptedAutoFetchIds.add(track.id)

            val data = withContext(Dispatchers.IO) {
                RomajiConverter.ensureLoaded(getApplication())
                LyricsRepository.resolveLyrics(
                    context = getApplication(),
                    track = track,
                    autoFetchOnline = shouldAutoFetch,
                    translateLang = state.translateLang
                )
            }
            if (_uiState.value.currentTrack?.id == track.id) {
                val active = computeActiveLyricIndex(data, _uiState.value.positionMs)
                _uiState.update {
                    it.copy(
                        lyrics = data,
                        lyricsLoading = false,
                        activeLyricIndex = active,
                        dictionaryReady = RomajiConverter.isReady(),
                        statusBanner = data.translationError.takeIf { err -> err.isNotBlank() }
                    )
                }
            }
        }
    }

    fun fetchOnlineLyricsNow() {
        val track = _uiState.value.currentTrack ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(lyricsLoading = true, statusBanner = "Mencari lirik di LRCLIB...") }
            val (found, data) = withContext(Dispatchers.IO) {
                RomajiConverter.ensureLoaded(getApplication())
                LyricsRepository.forceFetchOnline(
                    context = getApplication(),
                    track = track,
                    translateLang = _uiState.value.translateLang
                )
            }
            if (_uiState.value.currentTrack?.id == track.id) {
                val active = computeActiveLyricIndex(data, _uiState.value.positionMs)
                _uiState.update {
                    it.copy(
                        lyrics = data,
                        lyricsLoading = false,
                        activeLyricIndex = active,
                        statusBanner = if (found) "Lirik ditemukan & dikonversi ke Romaji!" else "Lirik tidak ditemukan di LRCLIB."
                    )
                }
            }
        }
    }

    fun saveManualLyrics(rawText: String) {
        val track = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(lyricsLoading = true) }
            val data = withContext(Dispatchers.IO) {
                RomajiConverter.ensureLoaded(getApplication())
                if (rawText.isBlank()) {
                    LyricsRepository.clearCachedLyrics(getApplication(), track.id)
                    LyricsRepository.resolveLyrics(
                        context = getApplication(),
                        track = track,
                        autoFetchOnline = false,
                        translateLang = _uiState.value.translateLang
                    )
                } else {
                    LyricsRepository.saveCachedLyrics(getApplication(), track.id, LyricsSource.MANUAL, rawText.trim())
                    LyricsRepository.buildLyricsData(
                        context = getApplication(),
                        trackId = track.id,
                        source = LyricsSource.MANUAL,
                        rawText = rawText.trim(),
                        translateLang = _uiState.value.translateLang
                    )
                }
            }
            _uiState.update {
                it.copy(
                    lyrics = data,
                    lyricsLoading = false,
                    activeLyricIndex = computeActiveLyricIndex(data, it.positionMs),
                    statusBanner = "Lirik disimpan & dikonversi ke Romaji."
                )
            }
        }
    }

    suspend fun convertStandaloneText(text: String, translateLang: String): LyricsData {
        return withContext(Dispatchers.IO) {
            RomajiConverter.ensureLoaded(getApplication())
            LyricsRepository.buildLyricsData(
                context = getApplication(),
                trackId = null,
                source = LyricsSource.MANUAL,
                rawText = text,
                translateLang = translateLang
            )
        }
    }

    // --- Equalizer v2 ---
    fun setEqEnabled(enabled: Boolean) {
        EqualizerHelper.setEnabled(enabled)
        _uiState.update { it.copy(eqEnabled = enabled) }
    }

    fun applyEqPreset(preset: EqPreset) {
        EqualizerHelper.applyPreset(preset)
        _uiState.update { it.copy(eqPreset = preset.name) }
    }

    fun markEqCustom() {
        if (_uiState.value.eqPreset != "Custom") {
            _uiState.update { it.copy(eqPreset = "Custom") }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        lyricsJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
