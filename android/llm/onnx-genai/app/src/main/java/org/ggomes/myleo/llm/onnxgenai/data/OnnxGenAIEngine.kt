package org.ggomes.myleo.llm.onnxgenai.data

import ai.onnxruntime.genai.Generator
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ggomes.myleo.llm.onnxgenai.domain.ChatInference
import org.ggomes.myleo.llm.onnxgenai.domain.ChatMessage
import org.ggomes.myleo.llm.onnxgenai.domain.ChatModelInfo
import org.ggomes.myleo.llm.onnxgenai.domain.ChatRole
import org.ggomes.myleo.llm.onnxgenai.domain.GenerationParams

/**
 * [ChatInference] over ONNX Runtime GenAI's Java API (`ai.onnxruntime.genai`).
 *
 * Each `generate` call builds the Phi-4-mini prompt, encodes it, and streams
 * decoded tokens via a `Generator` + `TokenizerStream`.
 *
 * The prompt is formatted by hand rather than with `Tokenizer.applyChatTemplate`:
 * that native JNI path corrupts the heap on device (`Scudo ERROR: invalid chunk
 * state when deallocating`) inside ONNX Runtime GenAI 0.17.0.
 */
class OnnxGenAIEngine(
    private val modelDir: String,
    private val sizeBytes: Long = 0,
) : ChatInference, AutoCloseable {

    @Volatile private var model: Model? = null
    @Volatile private var tokenizer: Tokenizer? = null
    @Volatile private var generator: Generator? = null
    @Volatile private var lastTokenCount = 0

    override suspend fun loadModel(): ChatModelInfo = withContext(Dispatchers.Default) {
        val existingModel = model
        val existingTokenizer = tokenizer
        if (existingModel != null && existingTokenizer != null) {
            return@withContext ChatModelInfo(File(modelDir).name, BACKEND, 0, sizeBytes, SOURCE)
        }
        val start = System.currentTimeMillis()
        var created: Model? = null
        try {
            created = Model(modelDir)
            val createdTokenizer = Tokenizer(created)
            model = created
            tokenizer = createdTokenizer
        } catch (e: Exception) {
            // Don't leave a half-initialized engine behind.
            model = null
            tokenizer = null
            runCatching { created?.close() }
            throw e
        }
        ChatModelInfo(
            displayName = File(modelDir).name,
            backend = BACKEND,
            loadMs = System.currentTimeMillis() - start,
            sizeBytes = sizeBytes,
            sourceUrl = SOURCE,
        )
    }

    override fun generate(
        systemPrompt: String,
        messages: List<ChatMessage>,
        params: GenerationParams,
    ): Flow<String> = flow {
        val model = model ?: error("model not initialized")
        val tokenizer = tokenizer ?: error("tokenizer not initialized")
        params.validate()

        val turns = messages.filter { it.role != ChatRole.SYSTEM }
        require(turns.any { it.role == ChatRole.USER }) { "history must contain a user message" }

        val prompt = Phi4MiniChatTemplate.format(systemPrompt, turns)
        val sequences = tokenizer.encode(prompt)
        val generatorParams = GeneratorParams(model).apply {
            setSearchOption("do_sample", true)
            setSearchOption("temperature", params.temperature)
            setSearchOption("top_k", params.topK.toDouble())
            setSearchOption("top_p", params.topP)
            setSearchOption("max_length", (params.maxTokens + 1024).toDouble())
        }
        val activeGenerator = Generator(model, generatorParams)
        generator = activeGenerator
        val stream = tokenizer.createStream()
        try {
            activeGenerator.appendTokenSequences(sequences)
            while (!activeGenerator.isDone()) {
                currentCoroutineContext().ensureActive()
                activeGenerator.generateNextToken()
                val token = activeGenerator.getLastTokenInSequence(0)
                val piece = stream.decode(token)
                if (piece.isNotEmpty()) emit(piece)
            }
        } finally {
            runCatching { lastTokenCount = activeGenerator.tokenCount().toInt() }
            generator = null
            runCatching { stream.close() }
            runCatching { activeGenerator.close() }
            runCatching { generatorParams.close() }
            runCatching { sequences.close() }
        }
    }

    override suspend fun cancel() {
        // The generation loop checks coroutine cancellation each token.
    }

    override suspend fun tokenCount(): Int = lastTokenCount

    override fun close() {
        runCatching { generator?.close() }
        runCatching { tokenizer?.close() }
        runCatching { model?.close() }
        model = null
        tokenizer = null
    }

    private companion object {
        const val BACKEND = "ONNX Runtime GenAI (CPU)"
        const val SOURCE = "https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx"
    }
}
