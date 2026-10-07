package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.WordpieceTokenizer
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class WordpieceTokenizerTest {

    private val vocab = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "##s")

    @Test
    fun tokenize_addsClsSepPadsAndMasks() {
        val input = WordpieceTokenizer(vocab).tokenize("hello", maxSeqLen = 8)

        assertThat(input.ids.toList()).containsExactly(2, 4, 3, 0, 0, 0, 0, 0)
        assertThat(input.mask.toList()).containsExactly(1, 1, 1, 0, 0, 0, 0, 0)
        assertThat(input.segmentIds.toList()).containsExactly(0, 0, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun tokenize_unknownWord_usesUnk() {
        val input = WordpieceTokenizer(vocab).tokenize("xyz", maxSeqLen = 4)

        assertThat(input.ids.toList()).containsExactly(2, 1, 3, 0)
    }

    @Test
    fun tokenize_wordpieceSplit_usesContinuation() {
        val input = WordpieceTokenizer(vocab).tokenize("worlds", maxSeqLen = 6)

        assertThat(input.ids.toList()).containsExactly(2, 5, 6, 3, 0, 0)
    }

    @Test
    fun tokenize_missingUnk_throws() {
        assertThatThrownBy { WordpieceTokenizer(listOf("[PAD]")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
