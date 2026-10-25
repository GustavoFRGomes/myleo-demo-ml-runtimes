package org.ggomes.myleo.decision.litert.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JuliaModelLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun required_isTheSmallestUsablePackage() {
        assertThat(JuliaModelLocator.REQUIRED.map { it.name })
            .containsExactly(
                JuliaModelLocator.TOKENIZER_FILE,
                JuliaModelLocator.TOKEN_TABLE_FILE,
                JuliaModelLocator.GRAPH_512_FILE,
            )
        assertThat(JuliaModelLocator.REQUIRED.sumOf { it.bytes }).isEqualTo(416_066_536L)
    }

    @Test
    fun download_addsTheLongWindowGraph() {
        assertThat(JuliaModelLocator.DOWNLOAD).hasSize(4)
        assertThat(JuliaModelLocator.DOWNLOAD.sumOf { it.bytes }).isEqualTo(604_569_756L)
    }

    @Test
    fun locate_emptyDirectory_isNull() {
        assertThat(JuliaModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun locate_requiredFilesOnly_returnsTheShortWindowPackage() {
        JuliaModelLocator.REQUIRED.forEach { temp.newFile(it.name) }

        val files = JuliaModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.windows).containsExactly(512)
        assertThat(files.graph(1024)).isNull()
    }

    @Test
    fun locate_withTheLongWindowGraph_listsBothWindows() {
        JuliaModelLocator.DOWNLOAD.forEach { temp.newFile(it.name) }

        val files = JuliaModelLocator.locate(temp.root)

        assertThat(files).isNotNull
        assertThat(files!!.windows).containsExactly(512, 1024)
        assertThat(files.graph(1024)?.name).isEqualTo(JuliaModelLocator.GRAPH_1024_FILE)
    }

    @Test
    fun locate_missingOneRequiredFile_isNull() {
        temp.newFile(JuliaModelLocator.TOKENIZER_FILE)
        temp.newFile(JuliaModelLocator.TOKEN_TABLE_FILE)

        assertThat(JuliaModelLocator.locate(temp.root)).isNull()
    }

    @Test
    fun remoteUrl_pointsAtThePinnedRepository() {
        assertThat(JuliaModelLocator.remoteUrl("tokenizer.json"))
            .isEqualTo(
                "https://huggingface.co/litert-community/Julia-1-LiteRT/resolve/main/tokenizer.json",
            )
        assertThat(RemoteFile(JuliaModelLocator.GRAPH_512_FILE, 1L).url)
            .endsWith("/julia1_s512_fp32.tflite")
    }

    @Test
    fun graphFileName_followsThePublishedNames() {
        assertThat(JuliaModelLocator.graphFileName(512)).isEqualTo("julia1_s512_fp32.tflite")
        assertThat(JuliaModelLocator.graphFileName(1024)).isEqualTo("julia1_s1024_fp32.tflite")
    }
}
