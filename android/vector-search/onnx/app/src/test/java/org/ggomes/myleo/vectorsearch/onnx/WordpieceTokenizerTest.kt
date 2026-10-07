package org.ggomes.myleo.vectorsearch.onnx

import org.ggomes.myleo.vectorsearch.onnx.data.WordpieceTokenizer
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class WordpieceTokenizerTest {

    private val vocab = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]",
        "hello", "world", "##s", ",", "!",
    )

    private val tokenizer = WordpieceTokenizer(vocab)

    @Test
    fun basicTokenize_mixedCaseAndPunctuation_splitsCorrectly() {
        assertThat(tokenizer.basicTokenize("Hello, World!"))
            .containsExactly("hello", ",", "world", "!")
    }

    @Test
    fun wordpieceTokenize_splitWord_returnsContinuationIds() {
        // "worlds" -> ["world", "##s"] -> [6, 7]
        assertThat(tokenizer.wordpieceTokenize("worlds")).containsExactly(6, 7)
    }

    @Test
    fun wordpieceTokenize_unknownWord_returnsUnkId() {
        assertThat(tokenizer.wordpieceTokenize("qwertyuiop")).containsExactly(1)
    }

    @Test
    fun wordpieceTokenize_tooLongWord_returnsUnkId() {
        assertThat(tokenizer.wordpieceTokenize("a".repeat(101))).containsExactly(1)
    }

    @Test
    fun tokenize_shortText_addsClsSepPadsAndMask() {
        val out = tokenizer.tokenize("hello", 8)

        assertThat(out.ids.toList()).containsExactly(2, 5, 3, 0, 0, 0, 0, 0)
        assertThat(out.mask.toList()).containsExactly(1, 1, 1, 0, 0, 0, 0, 0)
        assertThat(out.segmentIds.toList()).containsExactly(0, 0, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun tokenize_longText_truncatesKeepingSep() {
        val out = tokenizer.tokenize("hello world hello world hello", 6)

        assertThat(out.ids.size).isEqualTo(6)
        assertThat(out.ids.last()).isEqualTo(3) // [SEP] kept
        assertThat(out.mask.toList()).containsExactly(1, 1, 1, 1, 1, 1)
    }

    @Test
    fun init_vocabWithoutUnk_throws() {
        assertThatThrownBy { WordpieceTokenizer(listOf("[PAD]", "[CLS]")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
