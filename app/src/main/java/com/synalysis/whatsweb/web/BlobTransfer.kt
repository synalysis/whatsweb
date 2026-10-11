package com.synalysis.whatsweb.web

import java.io.File
import java.io.FileOutputStream
import java.util.Base64

internal class BlobTransfer(
    private val root: File,
    private val maxBytes: Long = MAX_BYTES,
    private val maxChunkChars: Int = MAX_CHUNK_CHARS,
    private val maxChunks: Int = MAX_CHUNKS,
    private val maxConcurrent: Int = MAX_CONCURRENT,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    data class Piece(
        val id: String,
        val index: Int,
        val total: Int,
        val mime: String,
        val part: String,
    )

    sealed interface Offer {
        data object Pending : Offer
        data object Rejected : Offer
        data class Ready(val file: File, val mime: String) : Offer
    }

    private class Transfer(
        val total: Int,
        val mime: String,
        val dir: File,
        val received: BooleanArray,
        var bytes: Long,
        var updatedAt: Long,
    )

    private val transfers = LinkedHashMap<String, Transfer>()

    fun offer(piece: Piece, now: Long): Offer = synchronized(this) {
        sweep(now)
        if (!ID.matches(piece.id) || piece.part.length > maxChunkChars) return Offer.Rejected
        val mime = normalizeMime(piece.mime) ?: return Offer.Rejected
        if (piece.total !in 1..maxChunks || piece.index !in 0 until piece.total) return Offer.Rejected

        val existing = transfers[piece.id]
        val transfer = when {
            existing == null -> {
                if (transfers.size >= maxConcurrent) return Offer.Rejected
                val dir = File(root, piece.id)
                if (!dir.isDirectory && !dir.mkdirs()) return Offer.Rejected
                Transfer(piece.total, mime, dir, BooleanArray(piece.total), 0, now).also {
                    transfers[piece.id] = it
                }
            }
            existing.total != piece.total || existing.mime != mime -> {
                remove(piece.id)
                return Offer.Rejected
            }
            else -> existing
        }
        if (transfer.received[piece.index]) {
            transfer.updatedAt = now
            return Offer.Pending
        }
        val decoded = try {
            Base64.getMimeDecoder().decode(piece.part)
        } catch (_: IllegalArgumentException) {
            remove(piece.id)
            return Offer.Rejected
        }
        if (decoded.isEmpty() || transfer.bytes + decoded.size > maxBytes) {
            remove(piece.id)
            return Offer.Rejected
        }
        try {
            File(transfer.dir, piece.index.toString()).writeBytes(decoded)
        } catch (_: Exception) {
            remove(piece.id)
            return Offer.Rejected
        }
        transfer.received[piece.index] = true
        transfer.bytes += decoded.size
        transfer.updatedAt = now
        if (transfer.received.any { !it }) return Offer.Pending

        val complete = File(transfer.dir, "complete.bin")
        try {
            FileOutputStream(complete).use { output ->
                for (index in 0 until transfer.total) {
                    File(transfer.dir, index.toString()).inputStream().use { it.copyTo(output) }
                }
            }
        } catch (_: Exception) {
            remove(piece.id)
            return Offer.Rejected
        }
        for (index in 0 until transfer.total) File(transfer.dir, index.toString()).delete()
        transfers.remove(piece.id)
        Offer.Ready(complete, transfer.mime)
    }

    fun close() = synchronized(this) {
        transfers.keys.toList().forEach { remove(it) }
        root.deleteRecursively()
    }

    private fun sweep(now: Long) {
        transfers.filterValues { now - it.updatedAt > ttlMillis }.keys.toList().forEach { remove(it) }
    }

    private fun remove(id: String) {
        transfers.remove(id)?.dir?.deleteRecursively()
    }

    companion object {
        const val MAX_BYTES = 60L * 1024 * 1024
        const val SLICE_BYTES = 192 * 1024
        const val MAX_CHUNK_CHARS = 280_000
        const val MAX_CHUNKS = 360
        const val MAX_CONCURRENT = 2
        const val TTL_MILLIS = 60_000L

        private val ID = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,63}""")
        private val MIME = Regex("""[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+""")

        fun normalizeMime(raw: String): String? {
            val mime = raw.substringBefore(';').trim()
            if (mime.length !in 3..127 || !MIME.matches(mime)) return null
            return mime
        }
    }
}
