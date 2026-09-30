package org.ggomes.myleo.llm.litertlm.data

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ggomes.myleo.llm.litertlm.domain.ChatInference
import org.ggomes.myleo.llm.litertlm.domain.ChatMessage
import org.ggomes.myleo.llm.litertlm.domain.ChatModelInfo
import org.ggomes.myleo.llm.litertlm.domain.ChatRole
import org.ggomes.myleo.llm.litertlm.domain.GenerationParams

/**
 * [ChatInference] over LiteRT-LM's Kotlin `Engine`/`Conversation` API.
 *
 * Each `generate` call builds a fresh conversation from the full history and
 * streams the reply (`Conversation.sendMessageAsync` → `Flow<Message>`).
 * Rebuilding per turn keeps regenerate/clear trivial; a KV-cache-reusing
 * conversation can replace this behind the same interface.
 */
class LiteRTLMEngine(
    private val modelPath: String,
    private val backend: Backend = Backend.GPU(),
) : ChatInference, AutoCloseable {

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var lastTokenCount = 0

    override suspend fun loadModel(): ChatModelInfo = withContext(Dispatchers.Default) {
        engine?.let { return@withContext infoFor(modelPath) }
        val start = System.currentTimeMillis()
        val created = Engine(EngineConfig(modelPath = modelPath, backend = backend))
        created.initialize()
        engine = created
        val ms = System.currentTimeMillis() - start
        ChatModelInfo(
            displayName = File(modelPath).name,
            backend = backendName(backend),
            loadMs = ms,
            sizeBytes = File(modelPath).length(),
            sourceUrl = null,
        )
    }

    override fun generate(
        systemPrompt: String,
        messages: List<ChatMessage>,
        params: GenerationParams,
    ): Flow<String> = flow {
        val engine = engine ?: error("engine not initialized")
        params.validate()
        val turns = messages.filter { it.role != ChatRole.SYSTEM }
        val prompt = turns.lastOrNull { it.role == ChatRole.USER }
            ?: error("history must end with a user message")

        val config = ConversationConfig(
            systemInstruction = systemPrompt.trim().takeIf { it.isNotEmpty() }?.let { Contents.of(it) },
            initialMessages = turns.dropLast(1).map(::toLiteMessage),
            samplerConfig = SamplerConfig(
                topK = params.topK,
                topP = params.topP,
                temperature = params.temperature,
            ),
            maxOutputToken = params.maxTokens,
        )
        val active = engine.createConversation(config)
        conversation = active
        try {
            // sendMessageAsync(Flow) yields incremental text chunks.
            active.sendMessageAsync(Message.user(prompt.text)).collect { message ->
                val piece = message.toString()
                if (piece.isNotEmpty()) emit(piece)
            }
        } finally {
            runCatching { lastTokenCount = active.getTokenCount() }
            conversation = null
            active.close()
        }
    }

    override suspend fun cancel() {
        runCatching { conversation?.cancelProcess() }
    }

    override suspend fun tokenCount(): Int =
        conversation?.let { runCatching { it.getTokenCount() }.getOrDefault(lastTokenCount) }
            ?: lastTokenCount

    override fun close() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        engine = null
    }

    private fun toLiteMessage(message: ChatMessage): Message =
        if (message.role == ChatRole.ASSISTANT) Message.model(message.text) else Message.user(message.text)

    private fun infoFor(path: String) = ChatModelInfo(
        displayName = File(path).name,
        backend = backendName(backend),
        loadMs = 0,
        sizeBytes = File(path).length(),
        sourceUrl = null,
    )

    private fun backendName(backend: Backend): String = when (backend) {
        is Backend.GPU -> "GPU"
        is Backend.CPU -> "CPU"
        is Backend.NPU -> "NPU"
        else -> backend.name
    }
}
