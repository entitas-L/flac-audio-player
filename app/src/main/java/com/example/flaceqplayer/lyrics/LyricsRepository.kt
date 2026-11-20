package com.example.flaceqplayer.lyrics

import android.content.Context
import com.example.flaceqplayer.model.FlacTrack
import com.example.flaceqplayer.model.LyricLine
import com.example.flaceqplayer.model.LyricsData
import com.example.flaceqplayer.model.LyricsSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object LyricsRepository {

    private val LRC_TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val LRC_META_REGEX = Regex("""^\[(ti|ar|al|au|by|length|offset|re|ve):(.*)]$""", RegexOption.IGNORE_CASE)

    private data class ParsedLine(val timeMs: Long?, val text: String)

    private fun cacheDir(context: Context): File {
        return File(context.filesDir, "lyrics_cache").apply { mkdirs() }
    }

    private fun lyricsFile(context: Context, trackId: String): File {
        return File(cacheDir(context), "${trackId}_lyrics.json")
    }

    private fun translationFile(context: Context, trackId: String, lang: String, rawText: String): File {
        val hash = sha1Short(rawText)
        return File(cacheDir(context), "${trackId}_tr_${lang}_$hash.json")
    }

    private fun sha1Short(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.take(8).joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun parseLrcOrPlain(rawText: String): Pair<Boolean, List<Pair<Long?, String>>> {
        if (rawText.isBlank()) return false to emptyList()
        var offsetMs = 0L
        val synced = mutableListOf<ParsedLine>()
        val plain = mutableListOf<ParsedLine>()

        for (rawLine in rawText.lines()) {
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()

            val metaMatch = LRC_META_REGEX.matchEntire(trimmed)
            if (metaMatch != null) {
                val tag = metaMatch.groupValues[1].lowercase(Locale.ROOT)
                if (tag == "offset") {
                    offsetMs = metaMatch.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                continue
            }

            val matches = LRC_TIME_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val cleanText = LRC_TIME_REGEX.replace(trimmed, "").trim()
                for (m in matches) {
                    val mins = m.groupValues[1].toLong()
                    val secs = m.groupValues[2].toLong()
                    val fracStr = m.groupValues[3]
                    val fracMs = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100L
                        2 -> fracStr.toLong() * 10L
                        else -> fracStr.substring(0, 3).toLong()
                    }
                    val ts = maxOf(0L, mins * 60_000L + secs * 1000L + fracMs + offsetMs)
                    synced.add(ParsedLine(ts, cleanText))
                }
            } else {
                plain.add(ParsedLine(null, line))
            }
        }

        if (synced.isNotEmpty()) {
            synced.sortBy { it.timeMs }
            return true to synced.map { it.timeMs to it.text }
        }

        while (plain.isNotEmpty() && plain.first().text.isBlank()) plain.removeAt(0)
        while (plain.isNotEmpty() && plain.last().text.isBlank()) plain.removeAt(plain.size - 1)
        return false to plain.map { null to it.text }
    }

    private fun readSidecarLrc(track: FlacTrack): String? {
        if (track.filePath.isBlank()) return null
        return try {
            val audioFile = File(track.filePath)
            val base = audioFile.nameWithoutExtension
            val parent = audioFile.parentFile ?: return null
            val candidate = listOf(File(parent, "$base.lrc"), File(parent, "$base.LRC"))
                .firstOrNull { it.isFile && it.canRead() }
            candidate?.readText(StandardCharsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCachedLyrics(context: Context, trackId: String): Pair<LyricsSource, String>? {
        val file = lyricsFile(context, trackId)
        if (!file.isFile) return null
        return try {
            val obj = JSONObject(file.readText(StandardCharsets.UTF_8))
            val srcName = obj.optString("source", "")
            val raw = obj.optString("raw", "")
            val src = LyricsSource.entries.firstOrNull { it.name == srcName } ?: LyricsSource.MANUAL
            if (raw.isNotBlank()) src to raw else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveCachedLyrics(context: Context, trackId: String, source: LyricsSource, rawText: String) {
        try {
            val obj = JSONObject().apply {
                put("source", source.name)
                put("raw", rawText)
            }
            lyricsFile(context, trackId).writeText(obj.toString(), StandardCharsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun clearCachedLyrics(context: Context, trackId: String) {
        try {
            lyricsFile(context, trackId).delete()
        } catch (_: Exception) {
        }
    }

    fun resolveLyrics(
        context: Context,
        track: FlacTrack,
        autoFetchOnline: Boolean,
        translateLang: String
    ): LyricsData {
        val cached = loadCachedLyrics(context, track.id)
        val embedded = track.embeddedLyrics.trim()
        val (embSynced, _) = if (embedded.isNotEmpty()) parseLrcOrPlain(embedded) else false to emptyList()
        val sidecar = readSidecarLrc(track)
        val (sideSynced, _) = if (!sidecar.isNullOrEmpty()) parseLrcOrPlain(sidecar) else false to emptyList()

        var chosenSource = LyricsSource.NONE
        var chosenRaw = ""

        if (cached != null && cached.first == LyricsSource.MANUAL) {
            chosenSource = LyricsSource.MANUAL
            chosenRaw = cached.second
        } else if (cached != null && cached.first == LyricsSource.LRCLIB) {
            val (lrcSynced, _) = parseLrcOrPlain(cached.second)
            if (lrcSynced || (!embSynced && !sideSynced)) {
                chosenSource = LyricsSource.LRCLIB
                chosenRaw = cached.second
            }
        }

        if (chosenRaw.isEmpty()) {
            when {
                embSynced -> {
                    chosenSource = LyricsSource.EMBEDDED
                    chosenRaw = embedded
                }
                !sidecar.isNullOrEmpty() -> {
                    chosenSource = LyricsSource.SIDECAR_LRC
                    chosenRaw = sidecar
                }
                embedded.isNotEmpty() -> {
                    chosenSource = LyricsSource.EMBEDDED
                    chosenRaw = embedded
                }
            }
        }

        if (chosenRaw.isEmpty() && autoFetchOnline) {
            val fetched = fetchFromLrclib(track)
            if (fetched != null) {
                chosenSource = LyricsSource.LRCLIB
                chosenRaw = fetched
                saveCachedLyrics(context, track.id, LyricsSource.LRCLIB, fetched)
            }
        }

        if (chosenRaw.isEmpty()) {
            return LyricsData(source = LyricsSource.NONE)
        }

        return buildLyricsData(context, track.id, chosenSource, chosenRaw, translateLang)
    }

    fun forceFetchOnline(context: Context, track: FlacTrack, translateLang: String): Pair<Boolean, LyricsData> {
        val fetched = fetchFromLrclib(track)
        if (fetched != null) {
            saveCachedLyrics(context, track.id, LyricsSource.LRCLIB, fetched)
            return true to buildLyricsData(context, track.id, LyricsSource.LRCLIB, fetched, translateLang)
        }
        return false to resolveLyrics(context, track, autoFetchOnline = false, translateLang = translateLang)
    }

    fun buildLyricsData(
        context: Context,
        trackId: String?,
        source: LyricsSource,
        rawText: String,
        translateLang: String
    ): LyricsData {
        val (synced, entries) = parseLrcOrPlain(rawText)
        var translations: List<String>? = null
        var translationError = ""

        val lang = translateLang.trim().lowercase(Locale.ROOT)
        if ((lang == "id" || lang == "en") && entries.isNotEmpty()) {
            val cachedTr = trackId?.let { loadCachedTranslation(context, it, lang, rawText) }
            if (cachedTr != null) {
                translations = cachedTr
            } else {
                try {
                    val tr = translateLines(entries.map { it.second }, lang)
                    if (tr.any { it.isNotBlank() }) {
                        translations = tr
                        if (trackId != null) {
                            saveCachedTranslation(context, trackId, lang, rawText, tr)
                        }
                    } else {
                        translationError = "Terjemahan arti tidak tersedia saat ini."
                    }
                } catch (e: Exception) {
                    translationError = "Gagal menerjemahkan arti (${e.message ?: "offline"})."
                }
            }
        }

        val lines = entries.mapIndexed { idx, (timeMs, text) ->
            val conv = RomajiConverter.convertLine(context, text)
            LyricLine(
                index = idx,
                timeMs = timeMs,
                original = conv.original,
                romaji = conv.romaji,
                hiragana = conv.hiragana,
                segments = conv.segments,
                isJapanese = conv.isJapanese,
                translation = translations?.getOrNull(idx).orEmpty()
            )
        }

        return LyricsData(
            source = source,
            synced = synced,
            rawText = rawText,
            lines = lines,
            translationLang = if (translations != null) lang else "",
            translationError = translationError
        )
    }

    private fun loadCachedTranslation(context: Context, trackId: String, lang: String, rawText: String): List<String>? {
        val file = translationFile(context, trackId, lang, rawText)
        if (!file.isFile) return null
        return try {
            val arr = JSONArray(file.readText(StandardCharsets.UTF_8))
            (0 until arr.length()).map { arr.optString(it, "") }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCachedTranslation(
        context: Context,
        trackId: String,
        lang: String,
        rawText: String,
        items: List<String>
    ) {
        try {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            translationFile(context, trackId, lang, rawText).writeText(arr.toString(), StandardCharsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun httpGet(urlStr: String, timeoutMs: Int = 8000, userAgent: String = "FlacEqPlayerAndroid/1.0"): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFromLrclib(track: FlacTrack): String? {
        val cleanTitle = track.title.replace(Regex("""\s*\(Cover\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
        val titleParam = URLEncoder.encode(cleanTitle.ifEmpty { track.title }, "UTF-8")
        val artistParam = URLEncoder.encode(track.artist, "UTF-8")
        val sb = StringBuilder("https://lrclib.net/api/get?track_name=$titleParam&artist_name=$artistParam")
        if (track.album.isNotBlank() && track.album != "Unknown Album") {
            sb.append("&album_name=").append(URLEncoder.encode(track.album, "UTF-8"))
        }
        if (track.durationMs > 1000L) {
            sb.append("&duration=").append((track.durationMs / 1000L).toInt())
        }

        try {
            val json = httpGet(sb.toString())
            if (!json.isNullOrBlank()) {
                val obj = JSONObject(json)
                val synced = obj.optString("syncedLyrics", "").trim()
                if (synced.isNotEmpty()) return synced
                val plain = obj.optString("plainLyrics", "").trim()
                if (plain.isNotEmpty()) return plain
            }
        } catch (_: Exception) {
        }

        return try {
            val query = URLEncoder.encode("${track.artist} ${cleanTitle.ifEmpty { track.title }}".trim(), "UTF-8")
            val json = httpGet("https://lrclib.net/api/search?q=$query") ?: return null
            val arr = JSONArray(json)
            var bestText: String? = null
            var bestScore = -1
            val durSec = track.durationMs / 1000.0
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val synced = item.optString("syncedLyrics", "").trim()
                val plain = item.optString("plainLyrics", "").trim()
                if (synced.isEmpty() && plain.isEmpty()) continue
                var score = if (synced.isNotEmpty()) 10 else 5
                val itemDur = item.optDouble("duration", 0.0)
                if (durSec > 0 && itemDur > 0) {
                    val diff = kotlin.math.abs(itemDur - durSec)
                    if (diff < 3.0) score += 5 else if (diff < 10.0) score += 2
                }
                if (score > bestScore) {
                    bestScore = score
                    bestText = synced.ifEmpty { plain }
                }
            }
            bestText
        } catch (_: Exception) {
            null
        }
    }

    private fun translateLines(lines: List<String>, targetLang: String): List<String> {
        val result = MutableList(lines.size) { "" }
        val nonEmpty = lines.mapIndexedNotNull { idx, text ->
            val t = text.trim()
            if (t.isEmpty()) null else idx to t
        }
        if (nonEmpty.isEmpty()) return result

        val chunk = mutableListOf<Pair<Int, String>>()
        var chunkLen = 0

        fun flush(items: List<Pair<Int, String>>) {
            if (items.isEmpty()) return
            val joined = items.joinToString("\n") { it.second }
            val translated = translateTextWithFallback(joined, targetLang)
            val split = translated.lines()
            if (split.size == items.size) {
                items.zip(split).forEach { (orig, tr) ->
                    result[orig.first] = tr.trim()
                }
            } else {
                for ((origIdx, single) in items) {
                    try {
                        result[origIdx] = translateTextWithFallback(single, targetLang).trim()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        for (pair in nonEmpty) {
            if (chunk.isNotEmpty() && chunkLen + pair.second.length + 1 > 1100) {
                flush(chunk)
                chunk.clear()
                chunkLen = 0
            }
            chunk.add(pair)
            chunkLen += pair.second.length + 1
        }
        if (chunk.isNotEmpty()) flush(chunk)
        return result
    }

    private fun translateTextWithFallback(text: String, targetLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        try {
            val c5Url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$targetLang&q=$encoded"
            val body = httpGet(c5Url, timeoutMs = 9000, userAgent = "Mozilla/5.0")
            if (!body.isNullOrBlank()) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val first = arr.opt(0)
                    if (first is String && first.isNotBlank()) return first
                    if (first is JSONArray) {
                        val sb = StringBuilder()
                        for (i in 0 until arr.length()) {
                            sb.append(arr.optJSONArray(i)?.optString(0).orEmpty())
                        }
                        if (sb.isNotBlank()) return sb.toString()
                    }
                }
            }
        } catch (_: Exception) {
        }

        val mmUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=ja|$targetLang"
        val body = httpGet(mmUrl, timeoutMs = 9000, userAgent = "Mozilla/5.0")
        if (!body.isNullOrBlank()) {
            val obj = JSONObject(body)
            val tr = obj.optJSONObject("responseData")?.optString("translatedText", "").orEmpty()
            if (tr.isNotBlank()) return tr
        }
        throw IllegalStateException("Translation endpoints unavailable")
    }
}
