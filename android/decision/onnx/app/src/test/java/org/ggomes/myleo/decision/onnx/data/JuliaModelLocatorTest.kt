package org.ggomes.myleo.decision.onnx.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JuliaModelLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun required_isTheGraphItsExternalWeightsAndTheTokenizer() {
        assertThat(JuliaModelLocator.REQUIRED.map { it.name })
            .containsExactly(
                JuliaModelLocator.TOKENIZER_FILE,
                JuliaModelLocator.MODEL_FILE,
                JuliaModelLocator.WEIGHTS_FILE,
            )
        assertThat(JuliaModelLocator.REQUIRED.sumOf { it.bytes })
            .isEqualTo(JuliaModelLocator.TOTAL_BYTES)
            .isEqualTo(614_134_447L)
    }

    @Test
    fun download_isTheWholePackage() {
        assertThat(JuliaModelLocator.DOWNLOAD).isEqualTo(JuliaModelLocator.REQUIRED)
    }

    @Test
    fun locate_emptyDirectory_isNull() {
        assertThat(JuliaModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun locate_completePackage_returnsTokeniserAndModel() {
        JuliaModelLocator.REQUIRED.forEach { temp.newFile(it.name) }

        val files = JuliaModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.tokenizer.name).isEqualTo(JuliaModelLocator.TOKENIZER_FILE)
        assertThat(files.model.name).isEqualTo(JuliaModelLocator.MODEL_FILE)
        assertThat(files.weights.name).isEqualTo(JuliaModelLocator.WEIGHTS_FILE)
    }

    @Test
    fun locate_missingExternalWeights_isNull() {
        temp.newFile(JuliaModelLocator.TOKENIZER_FILE)
        temp.newFile(JuliaModelLocator.MODEL_FILE)

        assertThat(JuliaModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun remoteUrl_pointsAtThePinnedRepository() {
        assertThat(JuliaModelLocator.remoteUrl("model.onnx"))
            .isEqualTo(
                "https://huggingface.co/SupersonicLabs/Julia-1-ONNX/resolve/main/model.onnx",
            )
        assertThat(RemoteFile(JuliaModelLocator.MODEL_FILE, 1L).url)
            .endsWith("/model.onnx")
    }

    @Test
    fun folder_isTheOnnxVariant() {
        assertThat(JuliaModelLocator.FOLDER).isEqualTo("julia-1-onnx")
        assertThat(JuliaModelLocator.REPO).isEqualTo("SupersonicLabs/Julia-1-ONNX")
    }
}
