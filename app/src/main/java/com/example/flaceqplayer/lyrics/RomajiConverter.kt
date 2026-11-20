package com.example.flaceqplayer.lyrics

import android.content.Context
import com.example.flaceqplayer.model.LyricSegment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Offline Japanese (Kanji / Hiragana / Katakana) to Hepburn Romaji & Furigana converter
 * backed by the bundled IPADic + Kanwa compressed dictionary (assets/jp_dict.tsv.gz).
 */
object RomajiConverter {

    private data class DictEntry(
        val reading: String,
        val cost: Int,
        val pos: String
    )

    private data class DpNode(
        val prev: Int,
        val surface: String,
        val reading: String,
        val pos: String
    )

    data class ConvertedLine(
        val original: String,
        val romaji: String,
        val hiragana: String,
        val segments: List<LyricSegment>,
        val isJapanese: Boolean
    )

    @Volatile
    private var dict: HashMap<String, DictEntry>? = null
    private val loadLock = Any()

    private val OPEN_PUNCT = setOf('(', '[', '"', '\'', '「', '『', '（', '【')
    private val CLOSE_PUNCT = setOf(',', '.', '!', '?', ':', ';', ')', ']', '}', '\'', '"', '、', '。', '，', '．', '！', '？', '：', '；', '」', '』', '）', '】', '~', '〜')

    private val PUNCT_MAP = mapOf(
        '、' to ",",
        '。' to ".",
        '，' to ",",
        '．' to ".",
        '！' to "!",
        '？' to "?",
        '：' to ":",
        '；' to ";",
        '「' to "\"",
        '」' to "\"",
        '『' to "\"",
        '』' to "\"",
        '（' to "(",
        '）' to ")",
        '【' to "[",
        '】' to "]",
        '〜' to "~",
        '～' to "~",
        '・' to " ",
        '　' to " "
    )

    private val DIGRAPHS = mapOf(
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho", "シェ" to "she",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho", "チェ" to "che",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo", "ジェ" to "je",
        "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ティ" to "ti", "ディ" to "di", "テュ" to "tyu", "デュ" to "dyu",
        "トゥ" to "tu", "ドゥ" to "du",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "フュ" to "fyu",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴ" to "vu", "ヴェ" to "ve", "ヴォ" to "vo",
        "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        "イェ" to "ye", "クァ" to "kwa", "グァ" to "gwa"
    )

    private val MONOGRAPHS = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo",
        'ヴ' to "vu", 'ヵ' to "ka", 'ヶ' to "ke"
    )

    fun isReady(): Boolean = dict != null

    fun ensureLoaded(context: Context) {
        if (dict != null) return
        synchronized(loadLock) {
            if (dict != null) return
            val map = HashMap<String, DictEntry>(380_000)
            try {
                context.assets.open("jp_dict.tsv.gz").use { raw ->
                    GZIPInputStream(raw, 32 * 1024).use { gz ->
                        BufferedReader(InputStreamReader(gz, StandardCharsets.UTF_8), 32 * 1024).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val row = line ?: continue
                                val t1 = row.indexOf('\t')
                                if (t1 <= 0) continue
                                val t2 = row.indexOf('\t', t1 + 1)
                                if (t2 <= t1) continue
                                val t3 = row.indexOf('\t', t2 + 1)
                                if (t3 <= t2) continue
                                val surf = row.substring(0, t1)
                                val read = row.substring(t1 + 1, t2)
                                val cost = row.substring(t2 + 1, t3).toIntOrNull() ?: 6000
                                val pos = row.substring(t3 + 1)
                                map[surf] = DictEntry(read, cost, pos)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            dict = map
        }
    }

    fun hasJapanese(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if ((code in 0x3040..0x30FF) || (code in 0x3400..0x4DBF) || (code in 0x4E00..0x9FFF) || ch == '々' || ch == '〆' || ch == 'ヶ') {
                return true
            }
        }
        return false
    }

    private fun hasKanji(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if ((code in 0x3400..0x4DBF) || (code in 0x4E00..0x9FFF) || ch == '々') {
                return true
            }
        }
        return false
    }

    private fun isKanaChar(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x3041..0x3096) || (code in 0x30A1..0x30F6) || ch == 'ー'
    }

    private fun isAsciiWordChar(ch: Char): Boolean {
        return ch.code < 128 && (ch.isLetterOrDigit() || ch == '\'')
    }

    fun hiraganaToKatakana(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code in 0x3041..0x3096) {
                sb.append((code + 0x60).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun katakanaToHiragana(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code in 0x30A1..0x30F6) {
                sb.append((code - 0x60).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun katakanaToRomaji(kana: String): String {
        val text = hiraganaToKatakana(kana)
        val out = StringBuilder()
        var i = 0
        val n = text.length
        var sokuon = false

        while (i < n) {
            val ch = text[i]
            if (ch == 'ッ' || ch == 'っ') {
                sokuon = true
                i++
                continue
            }
            if (ch == 'ー') {
                if (out.isNotEmpty()) {
                    val last = out[out.length - 1]
                    if (last in "aiueo") out.append(last)
                }
                i++
                continue
            }
            if (ch == 'ン') {
                var nextRoma = ""
                if (i + 1 < n) {
                    if (i + 2 <= n - 1) {
                        val pair = text.substring(i + 1, i + 3)
                        nextRoma = DIGRAPHS[pair] ?: MONOGRAPHS[text[i + 1]] ?: ""
                    } else {
                        nextRoma = MONOGRAPHS[text[i + 1]] ?: ""
                    }
                }
                if (nextRoma.isNotEmpty() && nextRoma[0] in "aiueoy") {
                    out.append("n'")
                } else {
                    out.append('n')
                }
                sokuon = false
                i++
                continue
            }

            var step = 1
            val roma: String = if (i + 1 < n && DIGRAPHS.containsKey(text.substring(i, i + 2))) {
                step = 2
                DIGRAPHS[text.substring(i, i + 2)]!!
            } else if (MONOGRAPHS.containsKey(ch)) {
                MONOGRAPHS[ch]!!
            } else {
                PUNCT_MAP[ch] ?: ch.toString()
            }

            if (sokuon && roma.isNotEmpty()) {
                if (roma.startsWith("ch")) {
                    out.append('t')
                } else {
                    val first = roma[0]
                    if (first.isLetter() && first !in "aiueon") {
                        out.append(first)
                    }
                }
                sokuon = false
            }

            out.append(roma)
            i += step
        }
        return out.toString()
    }

    private fun segmentDp(text: String, dictionary: Map<String, DictEntry>): List<DpNode> {
        val n = text.length
        val inf = 1_000_000_000_000L
        val dp = LongArray(n + 1) { inf }
        val back = arrayOfNulls<DpNode>(n + 1)
        dp[0] = 0L

        for (i in 0 until n) {
            if (dp[i] >= inf) continue

            val maxEnd = minOf(n, i + 12)
            for (j in (i + 1)..maxEnd) {
                val sub = text.substring(i, j)
                val entry = dictionary[sub]
                if (entry != null) {
                    var effCost = entry.cost.toLong()
                    if ((sub == "は" || sub == "へ") &&
                        (i == 0 || text[i - 1].isWhitespace() || text[i - 1] in "、。「」『』（）")
                    ) {
                        effCost += 5000L
                    }
                    val score = dp[i] + effCost - (j - i) * 3200L
                    if (score < dp[j]) {
                        dp[j] = score
                        back[j] = DpNode(i, sub, entry.reading, entry.pos)
                    }
                }
            }

            val ch = text[i]
            if (isAsciiWordChar(ch)) {
                var j = i + 1
                while (j < n && isAsciiWordChar(text[j])) j++
                val sub = text.substring(i, j)
                val score = dp[i] + 500L - (j - i) * 3200L
                if (score < dp[j]) {
                    dp[j] = score
                    back[j] = DpNode(i, sub, sub, "ASCII")
                }
            } else if (isKanaChar(ch)) {
                var j = i + 1
                while (j <= minOf(n, i + 8)) {
                    val sub = text.substring(i, j)
                    val score = dp[i] + 6500L - (j - i) * 2800L
                    if (score < dp[j]) {
                        dp[j] = score
                        back[j] = DpNode(i, sub, sub, "KANA")
                    }
                    if (j >= n || !isKanaChar(text[j])) break
                    j++
                }
            } else {
                val j = i + 1
                val score = dp[i] + 9500L - 3200L
                if (score < dp[j]) {
                    dp[j] = score
                    back[j] = DpNode(i, ch.toString(), ch.toString(), "UNK")
                }
            }
        }

        val tokens = ArrayList<DpNode>()
        var cur = n
        while (cur > 0) {
            val node = back[cur] ?: DpNode(cur - 1, text.substring(cur - 1, cur), text.substring(cur - 1, cur), "UNK")
            tokens.add(node)
            cur = node.prev
        }
        tokens.reverse()
        return tokens
    }

    private fun shouldAttachToPrev(pos: String, surface: String): Boolean {
        if (surface.isEmpty()) return false
        if (surface.length == 1 && surface[0] in CLOSE_PUNCT) return true
        if (pos == "助動詞") return true
        if (pos == "助詞" && surface in setOf("て", "で", "ば", "ても", "でも")) return true
        return false
    }

    fun convertLine(context: Context, line: String): ConvertedLine {
        val stripped = line.trim()
        if (stripped.isEmpty()) {
            return ConvertedLine(line, "", "", emptyList(), isJapanese = false)
        }
        if (!hasJapanese(stripped)) {
            return ConvertedLine(
                original = line,
                romaji = stripped,
                hiragana = "",
                segments = listOf(LyricSegment(stripped, "", stripped, hasKanji = false)),
                isJapanese = false
            )
        }

        ensureLoaded(context)
        val dictionary = dict ?: emptyMap()
        val rawTokens = segmentDp(stripped, dictionary)

        val segments = mutableListOf<LyricSegment>()
        for (tok in rawTokens) {
            val surf = tok.surface
            if (surf.isBlank()) continue

            // Handle compound override split like 長い間 -> ナガイ|アイダ
            if (surf == "長い間" && tok.reading.contains('|')) {
                segments.add(LyricSegment("長い", "ながい", "nagai", hasKanji = true))
                segments.add(LyricSegment("間", "あいだ", "aida", hasKanji = true))
                continue
            }

            if (!hasJapanese(surf)) {
                val mapped = surf.map { PUNCT_MAP[it] ?: it.toString() }.joinToString("")
                segments.add(LyricSegment(surf, "", mapped, hasKanji = false))
                continue
            }

            val surfHasKanji = hasKanji(surf)
            val hira = if (surfHasKanji) katakanaToHiragana(tok.reading) else ""
            val roma = katakanaToRomaji(tok.reading)

            if (segments.isNotEmpty() && shouldAttachToPrev(tok.pos, surf)) {
                val prev = segments.last()
                val prevFirst = prev.surface.firstOrNull()
                if (prev.romaji.isNotEmpty() && (prevFirst == null || prevFirst !in OPEN_PUNCT) && hasJapanese(prev.surface)) {
                    val combinedHira = if (prev.readingHiragana.isNotEmpty() || hira.isNotEmpty()) {
                        (prev.readingHiragana.ifEmpty { katakanaToHiragana(prev.surface) }) +
                            (hira.ifEmpty { katakanaToHiragana(surf) })
                    } else ""
                    segments[segments.lastIndex] = LyricSegment(
                        surface = prev.surface + surf,
                        readingHiragana = combinedHira,
                        romaji = prev.romaji + roma,
                        hasKanji = prev.hasKanji || surfHasKanji
                    )
                    continue
                }
            }

            segments.add(LyricSegment(surf, hira, roma, surfHasKanji))
        }

        val romaParts = mutableListOf<String>()
        val hiraParts = mutableListOf<String>()
        for (seg in segments) {
            val r = seg.romaji.trim()
            val h = (seg.readingHiragana.ifEmpty { katakanaToHiragana(seg.surface) }).trim()
            if (r.isEmpty()) continue
            if (romaParts.isEmpty()) {
                romaParts.add(r)
                hiraParts.add(h)
            } else {
                val prevLast = romaParts.last().last()
                if (r[0] in CLOSE_PUNCT || prevLast in OPEN_PUNCT) {
                    romaParts[romaParts.lastIndex] = romaParts.last() + r
                    hiraParts[hiraParts.lastIndex] = hiraParts.last() + seg.surface
                } else {
                    romaParts.add(r)
                    hiraParts.add(h)
                }
            }
        }

        return ConvertedLine(
            original = line,
            romaji = romaParts.joinToString(" "),
            hiragana = hiraParts.joinToString(" "),
            segments = segments,
            isJapanese = true
        )
    }
}
