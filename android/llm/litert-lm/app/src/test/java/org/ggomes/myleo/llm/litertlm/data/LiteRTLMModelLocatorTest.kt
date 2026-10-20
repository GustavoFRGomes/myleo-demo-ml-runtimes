package org.ggomes.myleo.llm.litertlm.data

import android.content.Context
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Pins the download contract: the exact bundle filename, the repository it comes
 * from, and the destination path. A typo in any of the three turns into a silent
 * 404 at runtime, which unit tests can catch here instead.
 */
class LiteRTLMModelLocatorTest {

    private val context = mock<Context> {
        on { filesDir } doReturn File("/data/user/0/org.ggomes.myleo.llm.litertlm/files")
    }

    @Test
    fun fileName_isMiniCPM5GpuOptimizedBundle() {
        assertThat(LiteRTLMModelLocator.FILE_NAME).isEqualTo("minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm")
    }

    @Test
    fun remoteUrl_pointsAtTheMiniCPM5Repository() {
        assertThat(LiteRTLMModelLocator.remoteUrl()).isEqualTo(
            "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm",
        )
    }

    @Test
    fun modelFile_livesInTheModelsSubdirectory() {
        assertThat(LiteRTLMModelLocator.modelFile(context).path)
            .endsWith("/files/models/${LiteRTLMModelLocator.FILE_NAME}")
    }

    @Test
    fun locate_missingFile_isNull() {
        assertThat(LiteRTLMModelLocator.locate(context)).isNull()
    }

    @Test
    fun hint_namesTheDownloadSize() {
        assertThat(LiteRTLMModelLocator.HINT).contains("793 MB")
    }
}
