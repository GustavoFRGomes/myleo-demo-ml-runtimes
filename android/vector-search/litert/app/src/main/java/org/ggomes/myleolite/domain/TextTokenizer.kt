package org.ggomes.myleolite.domain

/** Fixed-length BERT-style model inputs for one text. All arrays have `maxSeqLen` entries. */
data class TokenizedInput(
    val ids: IntArray,
    val mask: IntArray,
    val segmentIds: IntArray,
)

/**
 * Converts raw text into model input IDs. Implementations own the vocabulary
 * (e.g. a WordPiece `vocab.txt` bundled in assets).
 */
interface TextTokenizer {
    fun tokenize(text: String, maxSeqLen: Int): TokenizedInput
}
