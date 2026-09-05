package com.freedomfighter.readerslauncher.books

import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** A parsed book: plain-text chapters only. Images, styles and footnote links are dropped. */
class Book(val title: String, val chapters: List<Chapter>) {
    class Chapter(val title: String, val paragraphs: List<String>) {
        val length: Int = paragraphs.sumOf { it.length + 1 }
    }
    val totalLength: Int = chapters.sumOf { it.length }
    fun charsBefore(chapter: Int): Int = chapters.take(chapter).sumOf { it.length }
}

enum class BookFormat { EPUB, MOBI, FB2, TXT }

class UnsupportedBookException(message: String) : Exception(message)

/**
 * Minimal readers for the common non-PDF formats: EPUB (zip + OPF spine), MOBI (PalmDoc
 * compression), FB2 (XML) and plain text. The aim is a stable text extraction, not fidelity.
 */
object BookParser {

    fun detect(file: File, nameHint: String): BookFormat {
        val head = ByteArray(68)
        RandomAccessFile(file, "r").use { it.read(head) }
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return BookFormat.EPUB
        if (String(head, 60, 8, Charsets.ISO_8859_1) == "BOOKMOBI") return BookFormat.MOBI
        val ext = nameHint.substringAfterLast('.', "").lowercase()
        if (ext == "fb2") return BookFormat.FB2
        val start = String(head, Charsets.ISO_8859_1)
        if (start.contains("<?xml") || start.contains("<FictionBook")) return BookFormat.FB2
        return when (ext) {
            "epub" -> BookFormat.EPUB
            "mobi", "prc", "azw", "azw3" -> BookFormat.MOBI
            else -> BookFormat.TXT
        }
    }

    fun parse(file: File, format: BookFormat, fallbackTitle: String): Book {
        val book = when (format) {
            BookFormat.EPUB -> parseEpub(file, fallbackTitle)
            BookFormat.MOBI -> parseMobi(file, fallbackTitle)
            BookFormat.FB2 -> parseFb2(file.readText(), fallbackTitle)
            BookFormat.TXT -> parseTxt(file.readText(), fallbackTitle)
        }
        if (book.chapters.isEmpty()) throw UnsupportedBookException("no text found")
        return Book(book.title, splitLongChapters(book.chapters))
    }

    // ---- EPUB ------------------------------------------------------------------------

    private fun parseEpub(file: File, fallbackTitle: String): Book {
        ZipFile(file).use { zip ->
            fun read(path: String): String? {
                val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/")) ?: return null
                return zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val container = read("META-INF/container.xml") ?: throw UnsupportedBookException("not an EPUB")
            val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
                ?: throw UnsupportedBookException("no OPF")
            val opf = read(opfPath) ?: throw UnsupportedBookException("OPF missing")
            val dir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

            val items = HashMap<String, Pair<String, String>>() // id -> (href, media-type)
            Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).forEach { m ->
                val tag = m.value
                val id = attr(tag, "id") ?: return@forEach
                val href = attr(tag, "href") ?: return@forEach
                items[id] = Pair(href, attr(tag, "media-type") ?: "")
            }
            val spine = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
                .mapNotNull { attr(it.value, "idref") }.toList()
            val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(opf)?.groupValues?.get(1)?.let { HtmlText.decode(it).trim() }?.ifBlank { null } ?: fallbackTitle

            // Chapter titles from the NCX (EPUB 2) or the nav document (EPUB 3), keyed by href.
            val titles = HashMap<String, String>()
            val ncxHref = items.values.firstOrNull { it.second.contains("dtbncx") }?.first
                ?: items.values.firstOrNull { it.first.endsWith(".ncx", true) }?.first
            ncxHref?.let { read(resolve(dir, it)) }?.let { ncx ->
                Regex("<navPoint\\b.*?</navPoint>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(ncx).forEach { np ->
                    val text = Regex("<text>(.*?)</text>", RegexOption.DOT_MATCHES_ALL).find(np.value)?.groupValues?.get(1)
                    val src = Regex("<content\\b[^>]*src=\"([^\"]+)\"").find(np.value)?.groupValues?.get(1)
                    if (text != null && src != null) titles.putIfAbsent(normalize(resolve(dir, src)), HtmlText.decode(text).trim())
                }
            }
            val navHref = Regex("<item\\b[^>]*properties=\"[^\"]*\\bnav\\b[^\"]*\"[^>]*>", RegexOption.IGNORE_CASE)
                .find(opf)?.value?.let { attr(it, "href") }
            navHref?.let { read(resolve(dir, it)) }?.let { nav ->
                Regex("<a\\b[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(nav).forEach { a ->
                    val target = normalize(resolve(dir + navHref.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }, a.groupValues[1]))
                    titles.putIfAbsent(target, HtmlText.strip(a.groupValues[2]).trim())
                }
            }

            val chapters = ArrayList<Book.Chapter>()
            spine.forEachIndexed { index, idref ->
                val (href, type) = items[idref] ?: return@forEachIndexed
                if (type.isNotEmpty() && !type.contains("html") && !type.contains("xml")) return@forEachIndexed
                val path = resolve(dir, href)
                val html = read(path) ?: return@forEachIndexed
                val paragraphs = HtmlText.toParagraphs(html)
                if (paragraphs.isEmpty()) return@forEachIndexed
                val chapterTitle = titles[normalize(path)] ?: HtmlText.heading(html) ?: "${index + 1}"
                chapters += Book.Chapter(chapterTitle, paragraphs)
            }
            return Book(title, chapters)
        }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            ?: Regex("\\b$name\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    private fun resolve(dir: String, href: String): String {
        val clean = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        val parts = ArrayList<String>()
        (dir + clean).split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }

    private fun normalize(path: String) = path.substringBefore('#').lowercase()

    // ---- MOBI (PalmDoc) --------------------------------------------------------------

    private fun parseMobi(file: File, fallbackTitle: String): Book {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes)
        val recordCount = buf.getShort(76).toInt() and 0xFFFF
        if (recordCount < 2) throw UnsupportedBookException("empty MOBI")
        val offsets = IntArray(recordCount + 1) { i -> if (i < recordCount) buf.getInt(78 + 8 * i) else bytes.size }
        val r0 = offsets[0]
        val compression = buf.getShort(r0).toInt() and 0xFFFF
        val textRecords = buf.getShort(r0 + 8).toInt() and 0xFFFF
        val encryption = buf.getShort(r0 + 12).toInt() and 0xFFFF
        if (encryption != 0) throw UnsupportedBookException("DRM")
        if (compression != 1 && compression != 2) throw UnsupportedBookException("HUFF/CDIC compression")
        var charset: Charset = Charsets.UTF_8
        var extraFlags = 0
        var title = String(bytes, 0, 32, Charsets.ISO_8859_1).trimEnd('\u0000').ifBlank { fallbackTitle }
        if (bytes.size > r0 + 20 && String(bytes, r0 + 16, 4, Charsets.ISO_8859_1) == "MOBI") {
            val headerLength = buf.getInt(r0 + 20)
            val encoding = buf.getInt(r0 + 28)
            if (encoding == 1252) charset = Charset.forName("windows-1252")
            if (headerLength >= 0xE4) extraFlags = buf.getShort(r0 + 0xF2).toInt() and 0xFFFF
            val exthFlag = buf.getInt(r0 + 0x80)
            if (exthFlag and 0x40 != 0) {
                val exth = r0 + 16 + headerLength
                if (String(bytes, exth, 4, Charsets.ISO_8859_1) == "EXTH") {
                    val count = buf.getInt(exth + 8)
                    var p = exth + 12
                    repeat(count) {
                        val type = buf.getInt(p); val len = buf.getInt(p + 4)
                        if (type == 503 && len > 8) title = String(bytes, p + 8, len - 8, charset)
                        p += len
                    }
                }
            }
        }
        val out = java.io.ByteArrayOutputStream()
        for (i in 1..minOf(textRecords, recordCount - 1)) {
            var end = offsets[i + 1]
            val start = offsets[i]
            // Strip trailing entries (bits 1..15), then the multibyte trailer (bit 0).
            for (bit in 15 downTo 1) if (extraFlags and (1 shl bit) != 0) {
                var size = 0; var shift = 0; var p = end - 1
                while (p >= start) {
                    val b = bytes[p].toInt() and 0xFF
                    size = size or ((b and 0x7F) shl shift); shift += 7
                    if (b and 0x80 != 0 || shift > 28 || p == start) break
                    p--
                }
                end -= size
            }
            if (extraFlags and 1 != 0) end -= ((bytes[end - 1].toInt() and 3) + 1)
            if (end <= start) continue
            val rec = bytes.copyOfRange(start, end)
            out.write(if (compression == 2) palmDocDecompress(rec) else rec)
        }
        val html = String(out.toByteArray(), charset)
        val sections = html.split(Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE))
        val chapters = sections.mapIndexedNotNull { i, s ->
            val paragraphs = HtmlText.toParagraphs(s)
            if (paragraphs.isEmpty()) null else Book.Chapter(HtmlText.heading(s) ?: "${i + 1}", paragraphs)
        }
        return Book(title, chapters)
    }

    private fun palmDocDecompress(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size * 2)
        val buf = ArrayList<Byte>(data.size * 2)
        var i = 0
        fun emit(b: Byte) { buf.add(b) }
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c == 0 -> emit(0)
                c in 1..8 -> { repeat(c) { if (i < data.size) emit(data[i++]) } }
                c <= 0x7F -> emit(c.toByte())
                c <= 0xBF -> {
                    if (i >= data.size) break
                    val v = (c shl 8) or (data[i].toInt() and 0xFF); i++
                    val distance = (v shr 3) and 0x7FF
                    val length = (v and 7) + 3
                    val startIdx = buf.size - distance
                    if (startIdx < 0) continue
                    repeat(length) { buf.add(buf[startIdx + it]) }
                }
                else -> { emit(' '.code.toByte()); emit((c xor 0x80).toByte()) }
            }
        }
        out.write(buf.toByteArray())
        return out.toByteArray()
    }

    // ---- FB2 / TXT ---------------------------------------------------------------------

    private fun parseFb2(xml: String, fallbackTitle: String): Book {
        val noBinary = Regex("<binary\\b.*?</binary>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(xml, "")
        val title = Regex("<book-title>(.*?)</book-title>", RegexOption.DOT_MATCHES_ALL).find(noBinary)?.groupValues?.get(1)
            ?.let { HtmlText.decode(it).trim() }?.ifBlank { null } ?: fallbackTitle
        val body = Regex("<body\\b.*?</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(noBinary)
            .joinToString("\n") { it.value }.ifEmpty { noBinary }
        val sections = body.split(Regex("<section\\b[^>]*>", RegexOption.IGNORE_CASE))
        val chapters = sections.mapIndexedNotNull { i, s ->
            val paragraphs = HtmlText.toParagraphs(s)
            if (paragraphs.isEmpty()) null
            else {
                val t = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(s)?.groupValues?.get(1)?.let { HtmlText.strip(it).trim() }
                Book.Chapter(t?.ifBlank { null } ?: "${i + 1}", paragraphs)
            }
        }
        return Book(title, chapters)
    }

    private fun parseTxt(text: String, fallbackTitle: String): Book {
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        return Book(fallbackTitle, listOf(Book.Chapter(fallbackTitle, paragraphs)))
    }

    /** Keep chapters under ~12k characters so a single text measurement stays quick. */
    private fun splitLongChapters(chapters: List<Book.Chapter>, max: Int = 12_000): List<Book.Chapter> {
        val out = ArrayList<Book.Chapter>()
        for (ch in chapters) {
            if (ch.length <= max) { out += ch; continue }
            var part = ArrayList<String>(); var size = 0; var n = 1
            for (p in ch.paragraphs) {
                if (size + p.length > max && part.isNotEmpty()) {
                    out += Book.Chapter(if (n == 1) ch.title else "${ch.title} · $n", part); n++
                    part = ArrayList(); size = 0
                }
                // A single monstrous paragraph is cut hard.
                if (p.length > max) {
                    var s = p
                    while (s.length > max) {
                        val cut = s.lastIndexOf(' ', max).let { if (it < max / 2) max else it }
                        part += s.substring(0, cut); s = s.substring(cut).trimStart()
                        out += Book.Chapter(if (n == 1) ch.title else "${ch.title} · $n", part); n++
                        part = ArrayList(); size = 0
                    }
                    if (s.isNotEmpty()) { part += s; size += s.length }
                } else { part += p; size += p.length + 1 }
            }
            if (part.isNotEmpty()) out += Book.Chapter(if (n == 1) ch.title else "${ch.title} · $n", part)
        }
        return out
    }
}

/** HTML/XHTML → paragraphs of plain text. Regex based on purpose: tolerant of sloppy markup. */
object HtmlText {
    private val dropBlocks = Regex("<(script|style|head|svg|math)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val comments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val lineBreak = Regex("<br\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val paragraphBreak = Regex(
        "</(p|div|h[1-6]|li|tr|blockquote|section|article|title|subtitle|dd|dt|pre|table|figcaption|aside|header|footer|epigraph|stanza|v)>" +
            "|<(p|div|h[1-6]|li|tr|blockquote|section|article|dd|dt|pre|table|figcaption|aside|header|footer)\\b[^>]*>" +
            "|<empty-line\\s*/?>|<hr\\b[^>]*/?>",
        RegexOption.IGNORE_CASE
    )
    private val anyTag = Regex("<[^>]+>")
    private val headingRe = Regex("<h[1-3]\\b[^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun toParagraphs(html: String): List<String> {
        var s = html
        s = dropBlocks.replace(s, " ")
        s = comments.replace(s, "")
        s = lineBreak.replace(s, "\n")
        s = paragraphBreak.replace(s, "\n\n")
        s = anyTag.replace(s, "")
        s = decode(s)
        s = s.replace(Regex("[ \\t\\r\\u00A0\\u2007\\u202F]+"), " ")
        return s.split(Regex("\n\\s*\n")).map { para ->
            para.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }.filter { it.isNotEmpty() }
    }

    fun strip(html: String): String = decode(anyTag.replace(html, "")).replace(Regex("\\s+"), " ")

    fun heading(html: String): String? =
        headingRe.find(html)?.groupValues?.get(1)?.let { strip(it).trim() }?.takeIf { it.isNotBlank() && it.length < 120 }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "laquo" to "«", "raquo" to "»", "shy" to "", "copy" to "©", "reg" to "®", "deg" to "°", "middot" to "·",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "agrave" to "à", "acirc" to "â", "ccedil" to "ç", "ugrave" to "ù",
        "ucirc" to "û", "ocirc" to "ô", "icirc" to "î", "iuml" to "ï", "euml" to "ë", "auml" to "ä", "ouml" to "ö", "uuml" to "ü",
        "szlig" to "ß", "ntilde" to "ñ", "oacute" to "ó", "iacute" to "í", "uacute" to "ú", "aacute" to "á", "atilde" to "ã", "otilde" to "õ",
        "Eacute" to "É", "Agrave" to "À", "Ccedil" to "Ç", "times" to "×", "euro" to "€", "pound" to "£", "sect" to "§"
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun decode(s: String): String = entity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { cp -> String(Character.toChars(cp)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { cp -> String(Character.toChars(cp)) } ?: m.value
            else -> named[e] ?: m.value
        }
    }
}
