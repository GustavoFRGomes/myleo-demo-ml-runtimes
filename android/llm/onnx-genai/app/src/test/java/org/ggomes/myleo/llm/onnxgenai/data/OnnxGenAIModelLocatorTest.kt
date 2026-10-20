package org.ggomes.myleo.llm.onnxgenai.data

import android.content.Context
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Pins the download contract: repository, the exact four packed files and the destination
 * path. A typo in any of them is a silent 404 at runtime.
 *
 * `genai_config.json` is deliberately *not* fetched — the int4 builder export does not
 * publish one — so it is listed as required-but-local, not as a remote file.
 */
class OnnxGenAIModelLocatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context by lazy {
        mock<Context> { on { filesDir } doReturn folder.root }
    }

    @Test
    fun folder_isTheInt4BuilderExport() {
        assertThat(OnnxGenAIModelLocator.FOLDER).isEqualTo("minicpm5-1b-onnx-int4")
    }

    @Test
    fun remoteFileUrl_pointsAtTheInt4RepositoryRoot() {
        val weights = OnnxGenAIModelLocator.files.first { it.name == "model.onnx" }

        assertThat(OnnxGenAIModelLocator.url(weights))
            .isEqualTo("https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4/resolve/main/model.onnx")
    }

    @Test
    fun files_areTheFourPackedFilesTheRepositoryPublishes() {
        assertThat(OnnxGenAIModelLocator.files.map { it.localName }).containsExactly(
            "model.onnx",
            "model.onnx.data",
            "tokenizer.json",
            "tokenizer_config.json",
        )
    }

    @Test
    fun files_neverFetchTheSynthesizedConfig() {
        assertThat(OnnxGenAIModelLocator.files.map { it.localName })
            .doesNotContain(MiniCPM5GenAIConfig.FILE_NAME)
    }

    @Test
    fun requiredFiles_areTheFourDownloadedFilesOnly() {
        assertThat(OnnxGenAIModelLocator.requiredFiles)
            .containsExactly(
                "model.onnx",
                "model.onnx.data",
                "tokenizer.json",
                "tokenizer_config.json",
            )
    }

    @Test
    fun packageDir_livesUnderModels() {
        assertThat(OnnxGenAIModelLocator.packageDir(context))
            .isEqualTo(File(folder.root, "models/minicpm5-1b-onnx-int4"))
    }

    @Test
    fun locate_missingPackage_isNull() {
        assertThat(OnnxGenAIModelLocator.locate(context)).isNull()
    }

    @Test
    fun locate_allDownloadedFilesPresent_returnsThePackageDir() {
        val dir = OnnxGenAIModelLocator.packageDir(context).apply { mkdirs() }
        OnnxGenAIModelLocator.requiredFiles.forEach { File(dir, it).writeText("x") }

        assertThat(OnnxGenAIModelLocator.locate(context)).isEqualTo(dir)
    }

    @Test
    fun locate_configNotYetWritten_stillFindsThePackage() {
        // The engine writes the config; a fresh download without it must not look "absent".
        val dir = OnnxGenAIModelLocator.packageDir(context).apply { mkdirs() }
        OnnxGenAIModelLocator.requiredFiles.forEach { File(dir, it).writeText("x") }

        assertThat(OnnxGenAIModelLocator.locate(context)).isEqualTo(dir)
        assertThat(File(dir, MiniCPM5GenAIConfig.FILE_NAME)).doesNotExist()
    }

    @Test
    fun locate_oneEmptyWeightFile_isNull() {
        val dir = OnnxGenAIModelLocator.packageDir(context).apply { mkdirs() }
        OnnxGenAIModelLocator.requiredFiles.forEach { File(dir, it).writeText("x") }
        File(dir, OnnxGenAIModelLocator.WEIGHTS_DATA_FILE).writeText("")

        assertThat(OnnxGenAIModelLocator.locate(context)).isNull()
    }

    @Test
    fun hint_namesTheDownloadSize() {
        assertThat(OnnxGenAIModelLocator.HINT).contains("1.4 GB")
    }
}
