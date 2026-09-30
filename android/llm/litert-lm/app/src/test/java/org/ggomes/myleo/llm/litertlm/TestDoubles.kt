package org.ggomes.myleo.llm.litertlm

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ggomes.myleo.llm.litertlm.data.ModelDownloader
import org.ggomes.myleo.llm.litertlm.domain.ChatInference
import org.ggomes.myleo.llm.litertlm.domain.ChatMessage
import org.ggomes.myleo.llm.litertlm.domain.ChatModelInfo
import org.ggomes.myleo.llm.litertlm.domain.GenerationParams

/** Scripted `ChatInference` fake — no runtime. */
class FakeChatInference(
    private val chunks: List<String> = listOf("Hel", "lo"),
) : ChatInference {
    var loadError: Exception? = null
    var generationError: Exception? = null
    var tokenCounts: List<Int> = listOf(0, 7)
    private var tokenIndex = 0

    val generateCalls = mutableListOf<Pair<String, List<ChatMessage>>>()

    override suspend fun loadModel(): ChatModelInfo {
        loadError?.let { throw it }
        return ChatModelInfo("fake.litertlm", "GPU", 12, 1_000L, null)
    }

    override fun generate(
        systemPrompt: String,
        messages: List<ChatMessage>,
        params: GenerationParams,
    ): Flow<String> = flow {
        generateCalls += systemPrompt to messages
        generationError?.let { throw it }
        chunks.forEach { emit(it) }
    }

    override suspend fun cancel() = Unit

    override suspend fun tokenCount(): Int {
        val value = tokenCounts.getOrElse(tokenIndex) { tokenCounts.last() }
        if (tokenIndex < tokenCounts.size - 1) tokenIndex++
        return value
    }
}

/** Scripted `ModelDownloader` fake — never touches the network. */
class FakeModelDownloader : ModelDownloader {
    var error: Exception? = null
    var downloadCount = 0

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File {
        downloadCount++
        onProgress(50, 100)
        error?.let { throw it }
        onProgress(100, 100)
        return destination
    }
}
