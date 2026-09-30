package org.ggomes.myleo.llm.litertlm

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.llm.litertlm.domain.ChatRole
import org.ggomes.myleo.llm.litertlm.domain.GenerationParams
import org.ggomes.myleo.llm.litertlm.ui.chat.ChatViewModel
import org.ggomes.myleo.llm.litertlm.ui.chat.DownloadState
import org.ggomes.myleo.llm.litertlm.ui.chat.ModelStatus
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        inference: FakeChatInference = FakeChatInference(),
        downloader: FakeModelDownloader = FakeModelDownloader(),
        locate: () -> File? = { File("/tmp/fake.litertlm") },
    ) = ChatViewModel(
        locate = locate,
        makeInference = { inference },
        downloader = downloader,
        remoteUrl = "https://example.test/model",
        downloadTarget = { File("/tmp/target") },
        ioDispatcher = dispatcher,
    )

    @Test
    fun loadModel_missingFile_setsFailedAndOffersDownload() = runTest {
        val viewModel = viewModel(locate = { null })

        viewModel.loadModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat(viewModel.uiState.value.canDownload).isTrue()
    }

    @Test
    fun loadModel_success_setsReady() = runTest {
        val viewModel = viewModel()

        viewModel.loadModel()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertThat(status).isInstanceOf(ModelStatus.Ready::class.java)
        assertThat((status as ModelStatus.Ready).info.displayName).isEqualTo("fake.litertlm")
    }

    @Test
    fun send_blankInput_doesNothing() = runTest {
        val inference = FakeChatInference()
        val viewModel = viewModel(inference)
        viewModel.loadModel()
        advanceUntilIdle()

        viewModel.onInputChange("   ")
        viewModel.send()
        advanceUntilIdle()

        assertThat(inference.generateCalls).isEmpty()
        assertThat(viewModel.uiState.value.messages).isEmpty()
    }

    @Test
    fun send_streamsChunksIntoAssistantMessage() = runTest {
        val inference = FakeChatInference(chunks = listOf("Hello", " world"))
        val viewModel = viewModel(inference)
        viewModel.loadModel()
        advanceUntilIdle()

        viewModel.onInputChange("hi")
        viewModel.send()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.messages.map { it.role }).containsExactly(ChatRole.USER, ChatRole.ASSISTANT)
        assertThat(state.messages.last().text).isEqualTo("Hello world")
        assertThat(state.messages.last().isStreaming).isFalse()
        assertThat(state.input).isEmpty()
        assertThat(state.messages.last().metrics?.generatedTokens).isEqualTo(7)
    }

    @Test
    fun downloadModel_success_loadsModel() = runTest {
        val downloader = FakeModelDownloader()
        val viewModel = viewModel(downloader = downloader, locate = { File("/tmp/fake.litertlm") })

        viewModel.downloadModel()
        advanceUntilIdle()

        assertThat(downloader.downloadCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Finished)
        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    @Test
    fun downloadModel_failure_setsDownloadFailed() = runTest {
        val downloader = FakeModelDownloader().apply { error = RuntimeException("offline") }
        val viewModel = viewModel(downloader = downloader, locate = { null })

        viewModel.downloadModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Failed("offline"))
    }

    @Test
    fun paramsChange_clampsValues() = runTest {
        val viewModel = viewModel()

        viewModel.onParamsChange(GenerationParams(temperature = -1.0, topK = 0, topP = 5.0, maxTokens = 0))

        assertThat(viewModel.uiState.value.params.temperature).isEqualTo(0.0)
        assertThat(viewModel.uiState.value.params.topK).isEqualTo(1)
        assertThat(viewModel.uiState.value.params.topP).isEqualTo(1.0)
        assertThat(viewModel.uiState.value.params.maxTokens).isEqualTo(1)
    }

    @Test
    fun regenerate_replacesLastAssistantMessage() = runTest {
        val inference = FakeChatInference(chunks = listOf("first"))
        val viewModel = viewModel(inference)
        viewModel.loadModel()
        advanceUntilIdle()
        viewModel.onInputChange("hi")
        viewModel.send()
        advanceUntilIdle()

        viewModel.regenerate()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.messages.map { it.role })
            .containsExactly(ChatRole.USER, ChatRole.ASSISTANT)
    }

    @Test
    fun clear_emptiesThread() = runTest {
        val viewModel = viewModel()
        viewModel.loadModel()
        advanceUntilIdle()
        viewModel.onInputChange("hi")
        viewModel.send()
        advanceUntilIdle()

        viewModel.clear()

        assertThat(viewModel.uiState.value.messages).isEmpty()
    }
}
