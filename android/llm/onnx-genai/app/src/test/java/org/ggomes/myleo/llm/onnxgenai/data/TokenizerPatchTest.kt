package org.ggomes.myleo.llm.onnxgenai.data

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The patch is the only thing standing between MiniCPM5-1B and
 * `Invalid regex: \p{N}+` at load time, so its byte-level behaviour is pinned
 * here rather than discovered on a device.
 */
class TokenizerPatchTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** A trimmed but structurally faithful `tokenizer.json` pre-tokenizer section. */
    private val realistic = """
        {"pre_tokenizer":{"type":"Sequence","pretokenizers":[
          {"type":"Split","pattern":{"Regex":"\\p{N}{1,3}"},"behavior":"Isolated"},
          {"type":"Split","pattern":{"Regex":"(?i:'s|'t)| ?[^\\s\\p{L}\\p{N}]+|\\p{N}+| ?[^\\s]+"},"behavior":"Isolated"}
        ]}}
    """.trimIndent()

    private fun writeTokenizer(content: String): File =
        folder.newFile("tokenizer.json").apply { writeBytes(content.toByteArray(Charsets.UTF_8)) }

    @Test
    fun apply_unsupportedQuantifier_rewritesItToTheBoundedForm() {
        val file = writeTokenizer(realistic)

        val result = TokenizerPatch.apply(file)

        assertThat(result).isEqualTo(TokenizerPatch.Result.PATCHED)
        val patched = file.readText()
        assertThat(patched).doesNotContain("""\\p{N}+""")
        assertThat(patched.split("""\\p{N}{1,3}""")).hasSize(3) // the pre-existing one + the patched one
    }

    @Test
    fun apply_growsTheFileByExactlyFourBytes() {
        val file = writeTokenizer(realistic)
        val before = file.length()

        TokenizerPatch.apply(file)

        assertThat(file.length()).isEqualTo(before + 4)
    }

    @Test
    fun apply_secondRun_isANoOp() {
        val file = writeTokenizer(realistic)
        TokenizerPatch.apply(file)
        val once = file.readBytes()

        val result = TokenizerPatch.apply(file)

        assertThat(result).isEqualTo(TokenizerPatch.Result.ALREADY_PATCHED)
        assertThat(file.readBytes()).isEqualTo(once)
    }

    @Test
    fun apply_anchorAbsent_leavesTheFileUntouched() {
        val content = """{"pre_tokenizer":{"type":"ByteLevel"}}"""
        val file = writeTokenizer(content)

        val result = TokenizerPatch.apply(file)

        assertThat(result).isEqualTo(TokenizerPatch.Result.ALREADY_PATCHED)
        assertThat(file.readText()).isEqualTo(content)
    }

    @Test
    fun apply_missingFile_throwsInsteadOfSilentlySucceeding() {
        val missing = File(folder.root, "tokenizer.json")

        assertThatThrownBy { TokenizerPatch.apply(missing) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tokenizer.json missing")
    }
}
