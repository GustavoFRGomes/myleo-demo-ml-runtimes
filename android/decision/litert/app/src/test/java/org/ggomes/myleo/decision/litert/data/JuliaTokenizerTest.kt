package org.ggomes.myleo.decision.litert.data

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Structural tests against a small synthetic tokenizer.json. The real file is 34 MB and is
 * downloaded on device, never committed, so the algorithm's exact behaviour (Metaspace, merges,
 * added-token precedence, byte fallback, lstrip/rstrip) is pinned here instead.
 */
class JuliaTokenizerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun tokenizer(): JuliaTokenizer = JuliaTokenizer(writeTokenizer())

    private fun writeTokenizer(): File =
        temp.newFile("tokenizer.json").apply { writeText(TOKENIZER_JSON) }

    @Test
    fun init_readsTheVocabularyAndMerges() {
        val tokenizer = tokenizer()

        assertThat(tokenizer.vocabularySize).isEqualTo(17)
        assertThat(tokenizer.mergeCount).isEqualTo(3)
    }

    @Test
    fun encode_singleWord_appliesMerges() {
        assertThat(tokenizer().encode("ab").toList()).containsExactly(11)
    }

    @Test
    fun encode_multiWord_splitsOnMetaspaceBoundaries() {
        assertThat(tokenizer().encode("a b").toList()).containsExactly(9, 10)
    }

    @Test
    fun encode_emptyText_returnsNoIds() {
        assertThat(tokenizer().encode("").toList()).isEmpty()
    }

    @Test
    fun encode_addedToken_matchesBeforeNormalization() {
        assertThat(tokenizer().encode("x<mask>y").toList()).containsExactly(5, 12, 4, 5, 13)
    }

    @Test
    fun encode_addedTokenWithStrip_removesSurroundingWhitespace() {
        assertThat(tokenizer().encode("a <x> b").toList()).containsExactly(9, 16, 10)
    }

    @Test
    fun encode_unknownScalar_fallsBackToItsUtf8Bytes() {
        assertThat(tokenizer().encode("é").toList()).containsExactly(5, 14, 15)
    }

    @Test
    fun encode_specialTokens_wrapsWithBosAndEos() {
        assertThat(tokenizer().encodeTokens("ab", addSpecialTokens = true).toList())
            .containsExactly(2, 11, 1)
    }

    @Test
    fun init_rejectsATokenizerWithoutTheExpectedStructure() {
        val file = temp.newFile("broken.json").apply { writeText("""{"model":{"type":"WordPiece"}}""") }

        assertThatThrownBy { JuliaTokenizer(file) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        val TOKENIZER_JSON =
            """
            {
              "model": {
                "type": "BPE",
                "byte_fallback": true,
                "dropout": null,
                "unk_token": "<unk>",
                "ignore_merges": false,
                "vocab": {
                  "<pad>": 0,
                  "<eos>": 1,
                  "<bos>": 2,
                  "<unk>": 3,
                  "<mask>": 4,
                  "▁": 5,
                  "a": 6,
                  "b": 7,
                  "ab": 8,
                  "▁a": 9,
                  "▁b": 10,
                  "▁ab": 11,
                  "<0x78>": 12,
                  "<0x79>": 13,
                  "<0xC3>": 14,
                  "<0xA9>": 15,
                  "<x>": 16
                },
                "merges": [["▁", "a"], ["▁a", "b"], ["▁", "b"]]
              },
              "normalizer": {"type": "Replace", "pattern": {"String": " "}, "content": "▁"},
              "pre_tokenizer": {
                "type": "Metaspace",
                "replacement": "▁",
                "prepend_scheme": "always",
                "split": true
              },
              "added_tokens": [
                {"content": "<mask>", "id": 4, "normalized": false, "single_word": false, "lstrip": false, "rstrip": false},
                {"content": "<x>", "id": 16, "normalized": false, "single_word": false, "lstrip": true, "rstrip": true}
              ]
            }
            """.trimIndent()
    }
}
