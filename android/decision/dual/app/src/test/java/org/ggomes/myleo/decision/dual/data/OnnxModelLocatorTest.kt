package org.ggomes.myleo.decision.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnnxModelLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun required_isTheGraphItsExternalWeightsAndTheTokenizer() {
        assertThat(OnnxModelLocator.REQUIRED.map { it.name })
            .containsExactly(
                OnnxModelLocator.TOKENIZER_FILE,
                OnnxModelLocator.MODEL_FILE,
                OnnxModelLocator.WEIGHTS_FILE,
            )
        assertThat(OnnxModelLocator.REQUIRED.sumOf { it.bytes })
            .isEqualTo(OnnxModelLocator.TOTAL_BYTES)
            .isEqualTo(614_134_447L)
    }

    @Test
    fun download_isTheWholePackage() {
        assertThat(OnnxModelLocator.DOWNLOAD).isEqualTo(OnnxModelLocator.REQUIRED)
    }

    @Test
    fun locate_emptyDirectory_isNull() {
        assertThat(OnnxModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun locate_completePackage_returnsTokeniserAndModel() {
        OnnxModelLocator.REQUIRED.forEach { temp.newFile(it.name) }

        val files = OnnxModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.tokenizer.name).isEqualTo(OnnxModelLocator.TOKENIZER_FILE)
        assertThat(files.model.name).isEqualTo(OnnxModelLocator.MODEL_FILE)
        assertThat(files.weights.name).isEqualTo(OnnxModelLocator.WEIGHTS_FILE)
    }

    @Test
    fun locate_missingExternalWeights_isNull() {
        temp.newFile(OnnxModelLocator.TOKENIZER_FILE)
        temp.newFile(OnnxModelLocator.MODEL_FILE)

        assertThat(OnnxModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun remoteUrl_pointsAtThePinnedRepository() {
        assertThat(RemoteFile(OnnxModelLocator.MODEL_FILE, 1L, OnnxModelLocator.REPO).url)
            .isEqualTo(
                "https://huggingface.co/SupersonicLabs/Julia-1-ONNX/resolve/main/model.onnx",
            )
    }

    @Test
    fun folder_isTheOnnxVariant() {
        assertThat(OnnxModelLocator.FOLDER).isEqualTo("julia-1-onnx")
        assertThat(OnnxModelLocator.REPO).isEqualTo("SupersonicLabs/Julia-1-ONNX")
    }
}
