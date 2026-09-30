package org.ggomes.myleo.vectorsearch.dual.data

import org.ggomes.myleo.vectorsearch.dual.domain.TextTokenizer
import org.ggomes.myleo.vectorsearch.dual.domain.TokenizedInput

/**
 * BERT WordPiece tokenizer (basic tokenize + greedy longest-match-first).
 *
 * @param vocab ordered vocabulary lines (`vocab.txt`); the line index is the token id.
 * @param doLowerCase lowercase input before tokenizing (uncased models).
 */
class WordpieceTokenizer(
    vocab: List<String>,
    private val doLowerCase: Boolean = true,
    private val unkToken: String = "[UNK]",
    private val clsToken: String = "[CLS]",
    private val sepToken: String = "[SEP]",
    private val padToken: String = "[PAD]",
    private val maxInputCharsPerWord: Int = 100,
) : TextTokenizer {

    private val vocabIndex: Map<String, Int> =
        vocab.withIndex().associate { it.value to it.index }

    init {
        require(vocabIndex.containsKey(unkToken)) { "vocab must contain $unkToken" }
    }

    private fun tokenId(token: String): Int = vocabIndex[token] ?: vocabIndex.getValue(unkToken)

    override fun tokenize(text: String, maxSeqLen: Int): TokenizedInput {
        require(maxSeqLen >= 2) { "maxSeqLen must leave room for [CLS] and [SEP]" }
        val ids = mutableListOf(tokenId(clsToken))
        for (word in basicTokenize(text)) {
            if (ids.size >= maxSeqLen - 1) break // reserve room for [SEP]
            ids += wordpieceTokenize(word)
        }
        val truncated = (ids.take(maxSeqLen - 1) + tokenId(sepToken)).toIntArray()
        val mask = IntArray(maxSeqLen) { i -> if (i < truncated.size) 1 else 0 }
        val padded = IntArray(maxSeqLen) { i -> truncated.getOrElse(i) { tokenId(padToken) } }
        return TokenizedInput(ids = padded, mask = mask, segmentIds = IntArray(maxSeqLen))
    }

    internal fun basicTokenize(text: String): List<String> {
        val normalized = if (doLowerCase) text.lowercase() else text
        // Space out CJK chars so each becomes its own token.
        val spaced = buildString {
            for (c in normalized) {
                if (isCjk(c)) {
                    append(' ')
                    append(c)
                    append(' ')
                } else {
                    append(c)
                }
            }
        }
        // Split on whitespace, then split punctuation into single-char tokens.
        return spaced.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .flatMap { splitPunctuation(it) }
    }

    private fun splitPunctuation(word: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in word) {
            if (isPunctuation(c)) {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
                out += c.toString()
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    internal fun wordpieceTokenize(word: String): List<Int> {
        if (word.length > maxInputCharsPerWord) return listOf(tokenId(unkToken))
        val ids = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = buildString {
                    if (start > 0) append("##")
                    append(word, start, end)
                }
                val id = vocabIndex[piece]
                if (id != null) {
                    found = id
                    break
                }
                end--
            }
            if (found == null) return listOf(tokenId(unkToken))
            ids += found
            start = end
        }
        return ids
    }

    private fun isPunctuation(c: Char): Boolean {
        if (c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~') return true
        return when (Character.getType(c).toByte()) {
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            Character.START_PUNCTUATION,
            -> true
            else -> false
        }
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF ||
            c.code in 0x20000..0x2A6DF || c.code in 0xF900..0xFAFF
}
