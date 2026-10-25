package org.ggomes.myleo.decision.litert.data

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Read-only float16 token table from `litert-community/Julia-1-LiteRT`
 * (`julia1_token_table_fp16.bin`, `[256000, 384]`, little-endian). The graph takes embeddings,
 * not ids, so the host looks each token's row up and gathers fp32 rows into the input buffer.
 *
 * The table is memory-mapped: 197 MB is never copied. Positions past [gather]'s ids gather the
 * real `<pad>` row (id 0), not a zero vector, exactly like the author's NumPy lookup.
 *
 * [vocabularySize]/[width] are constructor parameters only so the JVM tests can use a tiny
 * table; the production call site uses the defaults.
 */
class JuliaTokenTable(
    tableFile: File,
    val vocabularySize: Int = VOCABULARY_SIZE,
    val width: Int = WIDTH,
    private val padId: Int = PAD_ID,
) : Closeable {

    /** Bytes the file must contain for this geometry. */
    val sizeBytes: Long = vocabularySize.toLong() * width * 2

    private var table: ByteBuffer?

    init {
        require(vocabularySize > 0 && width > 0) { "table geometry must be positive" }
        require(padId in 0 until vocabularySize) { "pad id out of range: $padId" }
        require(tableFile.isFile && tableFile.length() == sizeBytes) {
            "Invalid token table size: ${tableFile.length()} B; expected $sizeBytes B"
        }
        // Closing the channel leaves the read-only mapping valid; the JVM owns the mapping.
        table = FileInputStream(tableFile).channel.use {
            it.map(FileChannel.MapMode.READ_ONLY, 0, sizeBytes).order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    /**
     * Fill row-major `[window, width]`: rows for [ids], then `<pad>` rows for the remaining
     * positions up to [window]. [destination] lets the engine reuse one buffer across decisions.
     */
    fun gather(
        ids: IntArray,
        window: Int,
        destination: FloatArray = FloatArray(window * width),
    ): FloatArray {
        val mapped = checkNotNull(table) { "JuliaTokenTable is closed" }
        require(window >= ids.size) { "Sequence of ${ids.size} ids exceeds window $window" }
        require(destination.size == window * width) {
            "Embedding destination must contain window * width values"
        }
        ids.forEach { require(it in 0 until vocabularySize) { "Token id out of range: $it" } }

        var output = 0
        for (position in 0 until window) {
            val token = if (position < ids.size) ids[position] else padId
            var offset = token * width * 2
            repeat(width) {
                destination[output++] = halfToFloat(mapped.getShort(offset).toInt())
                offset += 2
            }
        }
        return destination
    }

    /** Drops the engine's reference; the mapping is released with the JVM's own bookkeeping. */
    override fun close() {
        table = null
    }

    companion object {
        /** Vocabulary rows of the mmBERT-small tokenizer. */
        const val VOCABULARY_SIZE = 256_000

        /** Float16 values per token row (the encoder hidden size). */
        const val WIDTH = 384

        /** Padding gathers the real `<pad>` embedding row rather than a zero vector. */
        const val PAD_ID = 0

        /** Complete row-major binary16 table size: 256000 * 384 * 2 bytes. */
        const val SIZE_BYTES = 196_608_000L

        /** Exact binary16 to binary32 expansion: signed zero, subnormals and NaNs included. */
        fun halfToFloat(bits: Int): Float {
            val sign = (bits and 0x8000) shl 16
            val exponent = (bits ushr 10) and 0x1f
            val fraction = bits and 0x3ff
            val expanded =
                when (exponent) {
                    0 ->
                        if (fraction == 0) {
                            sign
                        } else {
                            val shift = Integer.numberOfLeadingZeros(fraction) - 21
                            sign or ((113 - shift) shl 23) or (((fraction shl shift) and 0x3ff) shl 13)
                        }
                    // IEEE conversion quiets signaling NaNs while retaining the sign and payload,
                    // as NumPy does.
                    31 -> sign or 0x7f800000 or (fraction shl 13) or (if (fraction == 0) 0 else 0x400000)
                    else -> sign or ((exponent + 112) shl 23) or (fraction shl 13)
                }
            return Float.fromBits(expanded)
        }
    }
}
