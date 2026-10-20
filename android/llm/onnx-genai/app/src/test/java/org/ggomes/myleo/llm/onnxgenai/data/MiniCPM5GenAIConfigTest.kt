package org.ggomes.myleo.llm.onnxgenai.data

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The published int4 package has no `genai_config.json`, so these tests pin the two
 * properties that make the graph loadable at all: a **static** KV cache
 * (`past_present_share_buffer`) and no `position_ids` input (RoPE is fused into
 * `GroupQueryAttention`).
 */
class MiniCPM5GenAIConfigTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json: String get() = MiniCPM5GenAIConfig.JSON

    @Test
    fun ensure_whenMissing_writesTheConfig() {
        val dir = folder.newFolder("model")

        val written = MiniCPM5GenAIConfig.ensure(dir)

        assertThat(written).exists()
        assertThat(written.name).isEqualTo("genai_config.json")
        assertThat(written.readText()).isEqualTo(MiniCPM5GenAIConfig.JSON)
    }

    @Test
    fun ensure_whenEmpty_rewritesTheConfig() {
        val dir = folder.newFolder("model")
        File(dir, MiniCPM5GenAIConfig.FILE_NAME).writeText("")

        MiniCPM5GenAIConfig.ensure(dir)

        assertThat(File(dir, MiniCPM5GenAIConfig.FILE_NAME).readText())
            .isEqualTo(MiniCPM5GenAIConfig.JSON)
    }

    @Test
    fun ensure_whenPresent_keepsTheExistingConfig() {
        val dir = folder.newFolder("model")
        val target = File(dir, MiniCPM5GenAIConfig.FILE_NAME).apply { writeText("""{"edited":true}""") }

        MiniCPM5GenAIConfig.ensure(dir)

        assertThat(target.readText()).isEqualTo("""{"edited":true}""")
    }

    @Test
    fun json_usesStaticPastPresentBuffer() {
        assertThat(json).contains("\"past_present_share_buffer\": true")
    }

    @Test
    fun json_omitsPositionIdsBecauseRopeIsFused() {
        assertThat(json).doesNotContain("position_ids")
    }

    @Test
    fun json_mapsThePastAndPresentTensorNames() {
        assertThat(json)
            .contains("\"past_key_names\": \"past_key_values.%d.key\"")
            .contains("\"past_value_names\": \"past_key_values.%d.value\"")
            .contains("\"present_key_names\": \"present.%d.key\"")
            .contains("\"present_value_names\": \"present.%d.value\"")
    }

    @Test
    fun json_describesTheCheckpointShape() {
        assertThat(json)
            .contains("\"num_hidden_layers\": 24")
            .contains("\"num_attention_heads\": 16")
            .contains("\"num_key_value_heads\": 2")
            .contains("\"head_size\": 128")
            .contains("\"vocab_size\": 130560")
            .contains("\"type\": \"llama\"")
    }

    @Test
    fun json_stopsOnBothEndOfTurnTokens() {
        assertThat(json).contains("\"eos_token_id\": [1, 130073]")
    }
}
