package mn.blazeapps.foxplayer.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import java.io.FileDescriptor

data class M4bChapter(
    val index: Int,
    val title: String,
    val startMs: Long,
    val durationMs: Long,
)

/**
 * Extracts chapter markers and timings directly from MP4 / M4B / M4A containers:
 * 1. QuickTime chapter text track (a dedicated track referenced by tref/chap or handler_type "text")
 * 2. Nero chpl atom (located in moov/udta/chpl)
 *
 * Direct atom traversal over the SAF ParcelFileDescriptor ensures reliable extraction
 * even where Android MediaExtractor or MediaMetadataRetriever cannot surface chapter tracks.
 */
object M4bChapterExtractor {

    private const val TAG = "M4bChapterExtractor"

    fun extract(context: Context, uri: Uri, totalDurationMs: Long = 0L): List<M4bChapter> {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                parsePfd(pfd, totalDurationMs)
            } ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "extract chapters failed for $uri: ${t.message}")
            emptyList()
        }
    }

    private fun parsePfd(pfd: ParcelFileDescriptor, totalDurationMs: Long): List<M4bChapter> {
        val fd: FileDescriptor = pfd.fileDescriptor
        val totalSize = Os.fstat(fd).st_size
        val moov = findTopLevelAtom(fd, totalSize, "moov") ?: return emptyList()
        val moovBytes = readBytes(fd, moov.payloadStart, moov.payloadSize.toInt())

        // 1. Try QuickTime chapter text track first
        val qtChapters = parseQuickTimeChapters(fd, moovBytes, totalDurationMs)
        if (qtChapters.isNotEmpty()) {
            return qtChapters
        }

        // 2. Try Nero chpl atom in moov / udta
        val neroChapters = parseNeroChapters(moovBytes, totalDurationMs)
        if (neroChapters.isNotEmpty()) {
            return neroChapters
        }

        return emptyList()
    }

    // --- QuickTime Chapter Track Parser ---------------------------------------

    private fun parseQuickTimeChapters(
        fd: FileDescriptor,
        moovBytes: ByteArray,
        totalDurationMs: Long,
    ): List<M4bChapter> {
        try {
            val traks = childAtomsOf(moovBytes, "trak")
            val chapTrackId = findChapterTrackIdFromAudioTref(moovBytes, traks)

            val chapterTrak = traks.firstOrNull { trak ->
                val tkhd = findChild(moovBytes, trak.payloadStart, trak.payloadSize.toInt(), "tkhd")
                    ?: return@firstOrNull false
                chapTrackId != null && readTkhdTrackId(moovBytes, tkhd) == chapTrackId
            } ?: traks.firstOrNull { trak ->
                handlerTypeOfTrak(moovBytes, trak) == "text"
            } ?: return emptyList()

            val trakOff = chapterTrak.payloadStart
            val trakSize = chapterTrak.payloadSize.toInt()
            val mdia = findChild(moovBytes, trakOff, trakSize, "mdia") ?: return emptyList()
            val mdhd = findChild(moovBytes, mdia.payloadStart, mdia.payloadSize.toInt(), "mdhd")
                ?: return emptyList()
            val timescale = readMdhdTimescale(moovBytes, mdhd)
            if (timescale <= 0) return emptyList()

            val minf = findChild(moovBytes, mdia.payloadStart, mdia.payloadSize.toInt(), "minf")
                ?: return emptyList()
            val stbl = findChild(moovBytes, minf.payloadStart, minf.payloadSize.toInt(), "stbl")
                ?: return emptyList()
            val stbStart = stbl.payloadStart
            val stbSize = stbl.payloadSize.toInt()

            val stts = findChild(moovBytes, stbStart, stbSize, "stts") ?: return emptyList()
            val stsz = findChild(moovBytes, stbStart, stbSize, "stsz") ?: return emptyList()
            val stsc = findChild(moovBytes, stbStart, stbSize, "stsc") ?: return emptyList()
            val stco = findChild(moovBytes, stbStart, stbSize, "stco")
                ?: findChild(moovBytes, stbStart, stbSize, "co64")
                ?: return emptyList()

            val sampleTimesUnits = readStts(moovBytes, stts)
            val sampleSizes = readStsz(moovBytes, stsz)
            val chunkOffsets = readChunkOffsets(moovBytes, stco)
            val sampleToChunk = readStsc(moovBytes, stsc)

            val sampleCount = minOf(sampleTimesUnits.size, sampleSizes.size)
            if (sampleCount == 0) return emptyList()
            val sampleFileOffsets = computeSampleFileOffsets(sampleCount, sampleSizes, chunkOffsets, sampleToChunk)

            val rawList = mutableListOf<Pair<String, Long>>()
            for (i in 0 until sampleCount) {
                val size = sampleSizes[i]
                val off = sampleFileOffsets.getOrNull(i) ?: continue
                if (size <= 0 || size > 65536) continue
                val raw = readBytes(fd, off, size)
                val title = decodeAppleTitleSample(raw) ?: continue
                val ms = ((sampleTimesUnits[i] * 1000.0) / timescale).toLong().coerceAtLeast(0L)
                rawList += title.trim() to ms
            }

            if (rawList.isEmpty()) return emptyList()
            val sorted = rawList.sortedBy { it.second }
            val chapters = mutableListOf<M4bChapter>()
            for (i in sorted.indices) {
                val (title, startMs) = sorted[i]
                val durationMs = if (i + 1 < sorted.size) {
                    (sorted[i + 1].second - startMs).coerceAtLeast(0L)
                } else {
                    if (totalDurationMs > startMs) totalDurationMs - startMs else 0L
                }
                chapters += M4bChapter(
                    index = i,
                    title = if (title.isBlank()) "Chapter ${i + 1}" else title,
                    startMs = startMs,
                    durationMs = durationMs,
                )
            }
            return chapters
        } catch (t: Throwable) {
            Log.d(TAG, "parseQuickTimeChapters encountered: ${t.message}")
            return emptyList()
        }
    }

    // --- Nero chpl Atom Parser ------------------------------------------------

    private fun parseNeroChapters(moovBytes: ByteArray, totalDurationMs: Long): List<M4bChapter> {
        try {
            // Find udta in moov
            val udta = findChild(moovBytes, 0L, moovBytes.size, "udta") ?: return emptyList()
            val chpl = findChild(moovBytes, udta.payloadStart, udta.payloadSize.toInt(), "chpl")
                ?: return emptyList()

            val s = chpl.payloadStart.toInt()
            val size = chpl.payloadSize.toInt()
            if (size < 9) return emptyList()

            // chpl atom payload:
            // 1 byte version
            // 3 bytes flags
            // 1 byte reserved (often 0)
            // 4 bytes chapter count (in some variants, 1 byte count at offset 5)
            var p = s + 4 // skip version & flags
            val count32 = beU32(moovBytes, p + 1)
            val (chapterCount, startDataOffset) = if (count32 in 1..5000) {
                count32 to (p + 5)
            } else {
                val count8 = moovBytes[p + 1].toInt() and 0xFF
                if (count8 > 0) count8 to (p + 2) else return emptyList()
            }

            p = startDataOffset
            val end = s + size
            val rawList = mutableListOf<Pair<String, Long>>()

            for (i in 0 until chapterCount) {
                if (p + 9 > end) break
                // 8 bytes timestamp in 100-nanoseconds (divide by 10,000 for ms)
                val time100ns = beU64(moovBytes, p)
                val startMs = (time100ns / 10_000L).coerceAtLeast(0L)
                p += 8

                val titleLen = moovBytes[p].toInt() and 0xFF
                p += 1
                if (p + titleLen > end) break
                val title = String(moovBytes, p, titleLen, Charsets.UTF_8).trim()
                p += titleLen
                rawList += title to startMs
            }

            if (rawList.isEmpty()) return emptyList()
            val sorted = rawList.sortedBy { it.second }
            val chapters = mutableListOf<M4bChapter>()
            for (i in sorted.indices) {
                val (title, startMs) = sorted[i]
                val durationMs = if (i + 1 < sorted.size) {
                    (sorted[i + 1].second - startMs).coerceAtLeast(0L)
                } else {
                    if (totalDurationMs > startMs) totalDurationMs - startMs else 0L
                }
                chapters += M4bChapter(
                    index = i,
                    title = if (title.isBlank()) "Chapter ${i + 1}" else title,
                    startMs = startMs,
                    durationMs = durationMs,
                )
            }
            return chapters
        } catch (t: Throwable) {
            Log.d(TAG, "parseNeroChapters encountered: ${t.message}")
            return emptyList()
        }
    }

    // --- MP4 Atom Walking Utilities -------------------------------------------

    private data class Atom(
        val type: String,
        val headerStart: Long,
        val payloadStart: Long,
        val payloadSize: Long,
    )

    private fun findTopLevelAtom(fd: FileDescriptor, total: Long, want: String): Atom? {
        var pos = 0L
        while (pos + 8 <= total) {
            val header = readBytes(fd, pos, 8)
            if (header.size < 8) break
            val rawSize = beU32(header, 0).toLong() and 0xFFFFFFFFL
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            val (payloadStart, payloadSize) = when (rawSize) {
                1L -> {
                    val ext = readBytes(fd, pos + 8, 8)
                    val big = beU64(ext, 0)
                    pos + 16 to (big - 16)
                }
                0L -> pos + 8 to (total - pos - 8)
                else -> pos + 8 to (rawSize - 8)
            }
            if (type == want) return Atom(type, pos, payloadStart, payloadSize)
            val nextPos = payloadStart + payloadSize
            if (nextPos <= pos) break
            pos = nextPos
        }
        return null
    }

    private fun childAtomsOf(parent: ByteArray, want: String): List<Atom> {
        val out = mutableListOf<Atom>()
        var p = 0
        while (p + 8 <= parent.size) {
            val size = (beU32(parent, p).toLong() and 0xFFFFFFFFL)
            val type = String(parent, p + 4, 4, Charsets.ISO_8859_1)
            val (payloadStart, payloadSize) = when (size) {
                1L -> {
                    val big = beU64(parent, p + 8)
                    (p + 16).toLong() to (big - 16)
                }
                0L -> (p + 8).toLong() to (parent.size - p - 8).toLong()
                else -> (p + 8).toLong() to (size - 8)
            }
            if (type == want) out += Atom(type, p.toLong(), payloadStart, payloadSize)
            val nextP = (payloadStart + payloadSize).toInt()
            if (nextP <= p) break
            p = nextP
        }
        return out
    }

    private fun findChild(parent: ByteArray, parentStart: Long, parentSize: Int, want: String): Atom? {
        val pStart = parentStart.toInt()
        var p = pStart
        val end = (pStart + parentSize).coerceAtMost(parent.size)
        while (p + 8 <= end) {
            val size = (beU32(parent, p).toLong() and 0xFFFFFFFFL)
            val type = String(parent, p + 4, 4, Charsets.ISO_8859_1)
            val (payloadStart, payloadSize) = when (size) {
                1L -> {
                    val big = beU64(parent, p + 8)
                    (p + 16).toLong() to (big - 16)
                }
                0L -> (p + 8).toLong() to (end - p - 8).toLong()
                else -> (p + 8).toLong() to (size - 8)
            }
            if (type == want) return Atom(type, p.toLong(), payloadStart, payloadSize)
            val nextP = (payloadStart + payloadSize).toInt()
            if (nextP <= p) break
            p = nextP
        }
        return null
    }

    private fun handlerTypeOfTrak(moov: ByteArray, trak: Atom): String? {
        val mdia = findChild(moov, trak.payloadStart, trak.payloadSize.toInt(), "mdia") ?: return null
        val hdlr = findChild(moov, mdia.payloadStart, mdia.payloadSize.toInt(), "hdlr") ?: return null
        val hStart = hdlr.payloadStart.toInt()
        if (hdlr.payloadSize < 12) return null
        return String(moov, hStart + 8, 4, Charsets.ISO_8859_1)
    }

    private fun findChapterTrackIdFromAudioTref(moov: ByteArray, traks: List<Atom>): Int? {
        for (trak in traks) {
            val handler = handlerTypeOfTrak(moov, trak)
            if (handler != "soun") continue
            val tref = findChild(moov, trak.payloadStart, trak.payloadSize.toInt(), "tref") ?: continue
            val chap = findChild(moov, tref.payloadStart, tref.payloadSize.toInt(), "chap") ?: continue
            if (chap.payloadSize >= 4) {
                return beU32(moov, chap.payloadStart.toInt())
            }
        }
        return null
    }

    private fun readTkhdTrackId(moov: ByteArray, tkhd: Atom): Int {
        val s = tkhd.payloadStart.toInt()
        val ver = moov[s].toInt() and 0xFF
        val off = if (ver == 1) 4 + 8 + 8 else 4 + 4 + 4
        return beU32(moov, s + off)
    }

    private fun readMdhdTimescale(moov: ByteArray, mdhd: Atom): Int {
        val s = mdhd.payloadStart.toInt()
        val ver = moov[s].toInt() and 0xFF
        val off = if (ver == 1) 4 + 8 + 8 else 4 + 4 + 4
        return beU32(moov, s + off)
    }

    private fun readStts(moov: ByteArray, stts: Atom): LongArray {
        val s = stts.payloadStart.toInt()
        val entryCount = beU32(moov, s + 4)
        val list = ArrayList<Long>()
        var t = 0L
        var p = s + 8
        repeat(entryCount) {
            if (p + 8 > moov.size) return@repeat
            val cnt = beU32(moov, p)
            val delta = beU32(moov, p + 4)
            p += 8
            repeat(cnt) {
                list += t
                t += delta.toLong()
            }
        }
        return list.toLongArray()
    }

    private fun readStsz(moov: ByteArray, stsz: Atom): IntArray {
        val s = stsz.payloadStart.toInt()
        val sampleSize = beU32(moov, s + 4)
        val sampleCount = beU32(moov, s + 8)
        return if (sampleSize != 0) {
            IntArray(sampleCount) { sampleSize }
        } else {
            val arr = IntArray(sampleCount)
            var p = s + 12
            for (i in 0 until sampleCount) {
                if (p + 4 > moov.size) break
                arr[i] = beU32(moov, p)
                p += 4
            }
            arr
        }
    }

    private fun readChunkOffsets(moov: ByteArray, stco: Atom): LongArray {
        val s = stco.payloadStart.toInt()
        val entryCount = beU32(moov, s + 4)
        val arr = LongArray(entryCount)
        return when (stco.type) {
            "stco" -> {
                var p = s + 8
                for (i in 0 until entryCount) {
                    if (p + 4 > moov.size) break
                    arr[i] = beU32(moov, p).toLong() and 0xFFFFFFFFL
                    p += 4
                }
                arr
            }
            "co64" -> {
                var p = s + 8
                for (i in 0 until entryCount) {
                    if (p + 8 > moov.size) break
                    arr[i] = beU64(moov, p)
                    p += 8
                }
                arr
            }
            else -> arr
        }
    }

    private data class StscEntry(val firstChunk: Int, val samplesPerChunk: Int)

    private fun readStsc(moov: ByteArray, stsc: Atom): List<StscEntry> {
        val s = stsc.payloadStart.toInt()
        val entryCount = beU32(moov, s + 4)
        val out = ArrayList<StscEntry>(entryCount)
        var p = s + 8
        for (i in 0 until entryCount) {
            if (p + 12 > moov.size) break
            val firstChunk = beU32(moov, p)
            val samplesPerChunk = beU32(moov, p + 4)
            p += 12
            out += StscEntry(firstChunk, samplesPerChunk)
        }
        return out
    }

    private fun computeSampleFileOffsets(
        sampleCount: Int,
        sampleSizes: IntArray,
        chunkOffsets: LongArray,
        stsc: List<StscEntry>,
    ): LongArray {
        if (chunkOffsets.isEmpty() || stsc.isEmpty()) return LongArray(0)
        val chunkCount = chunkOffsets.size
        val samplesPerChunk = IntArray(chunkCount)
        var entryIdx = 0
        for (ci in 0 until chunkCount) {
            val chunkNum = ci + 1
            while (entryIdx + 1 < stsc.size && stsc[entryIdx + 1].firstChunk <= chunkNum) entryIdx++
            samplesPerChunk[ci] = stsc[entryIdx].samplesPerChunk
        }
        val offsets = LongArray(sampleCount)
        var sampleIdx = 0
        for (ci in 0 until chunkCount) {
            var off = chunkOffsets[ci]
            val n = samplesPerChunk[ci]
            for (s in 0 until n) {
                if (sampleIdx >= sampleCount) return offsets
                offsets[sampleIdx] = off
                off += sampleSizes[sampleIdx].toLong()
                sampleIdx++
            }
        }
        return offsets
    }

    private fun decodeAppleTitleSample(raw: ByteArray): String? {
        if (raw.size < 2) return null
        val len = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
        return if (len in 1..(raw.size - 2)) {
            String(raw, 2, len, Charsets.UTF_8)
        } else {
            // Raw text fallback if length prefix not present
            val text = String(raw, Charsets.UTF_8).trim()
            text.takeIf { it.isNotBlank() && it.all { c -> c.code in 32..126 || c.isLetterOrDigit() || c.isWhitespace() } }
        }
    }

    private fun readBytes(fd: FileDescriptor, offset: Long, size: Int): ByteArray {
        val buf = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = Os.pread(fd, buf, read, size - read, offset + read)
            if (n <= 0) break
            read += n
        }
        return if (read == size) buf else buf.copyOf(read)
    }

    private fun beU32(buf: ByteArray, p: Int): Int =
        ((buf[p].toInt() and 0xFF) shl 24) or
            ((buf[p + 1].toInt() and 0xFF) shl 16) or
            ((buf[p + 2].toInt() and 0xFF) shl 8) or
            (buf[p + 3].toInt() and 0xFF)

    private fun beU64(buf: ByteArray, p: Int): Long =
        ((buf[p].toLong() and 0xFF) shl 56) or
            ((buf[p + 1].toLong() and 0xFF) shl 48) or
            ((buf[p + 2].toLong() and 0xFF) shl 40) or
            ((buf[p + 3].toLong() and 0xFF) shl 32) or
            ((buf[p + 4].toLong() and 0xFF) shl 24) or
            ((buf[p + 5].toLong() and 0xFF) shl 16) or
            ((buf[p + 6].toLong() and 0xFF) shl 8) or
            (buf[p + 7].toLong() and 0xFF)
}
