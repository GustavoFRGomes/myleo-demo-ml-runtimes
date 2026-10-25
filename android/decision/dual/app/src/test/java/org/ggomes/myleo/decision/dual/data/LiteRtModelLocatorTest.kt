package org.ggomes.myleo.decision.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiteRtModelLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun required_isTheSmallestUsablePackage() {
        assertThat(LiteRtModelLocator.REQUIRED.map { it.name })
            .containsExactly(
                LiteRtModelLocator.TOKENIZER_FILE,
                LiteRtModelLocator.TOKEN_TABLE_FILE,
                LiteRtModelLocator.GRAPH_512_FILE,
            )
        assertThat(LiteRtModelLocator.REQUIRED.sumOf { it.bytes }).isEqualTo(416_066_536L)
    }

    @Test
    fun download_addsTheLongWindowGraph() {
        assertThat(LiteRtModelLocator.DOWNLOAD).hasSize(4)
        assertThat(LiteRtModelLocator.DOWNLOAD.sumOf { it.bytes }).isEqualTo(604_569_756L)
    }

    @Test
    fun locate_emptyDirectory_isNull() {
        assertThat(LiteRtModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun locate_requiredFilesOnly_returnsTheShortWindowPackage() {
        LiteRtModelLocator.REQUIRED.forEach { temp.newFile(it.name) }

        val files = LiteRtModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.windows).containsExactly(512)
        assertThat(files.graph(1024)).isNull()
    }

    @Test
    fun locate_withTheLongWindowGraph_listsBothWindows() {
        LiteRtModelLocator.DOWNLOAD.forEach { temp.newFile(it.name) }

        val files = LiteRtModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.windows).containsExactly(512, 1024)
        assertThat(files.graph(1024)?.name).isEqualTo(LiteRtModelLocator.GRAPH_1024_FILE)
    }

    @Test
    fun locate_missingOneRequiredFile_isNull() {
        temp.newFile(LiteRtModelLocator.TOKENIZER_FILE)
        temp.newFile(LiteRtModelLocator.TOKEN_TABLE_FILE)

        assertThat(LiteRtModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun remoteUrl_pointsAtThePinnedRepository() {
        assertThat(RemoteFile(LiteRtModelLocator.GRAPH_512_FILE, 1L, LiteRtModelLocator.REPO).url)
            .isEqualTo(
                "https://huggingface.co/litert-community/Julia-1-LiteRT/resolve/main/julia1_s512_fp32.tflite",
            )
    }

    @Test
    fun graphFileName_followsThePublishedNames() {
        assertThat(LiteRtModelLocator.graphFileName(512)).isEqualTo("julia1_s512_fp32.tflite")
        assertThat(LiteRtModelLocator.graphFileName(1024)).isEqualTo("julia1_s1024_fp32.tflite")
    }

    @Test
    fun folder_isTheLitertVariant() {
        assertThat(LiteRtModelLocator.FOLDER).isEqualTo("julia-1-litert")
        assertThat(LiteRtModelLocator.REPO).isEqualTo("litert-community/Julia-1-LiteRT")
    }
}
