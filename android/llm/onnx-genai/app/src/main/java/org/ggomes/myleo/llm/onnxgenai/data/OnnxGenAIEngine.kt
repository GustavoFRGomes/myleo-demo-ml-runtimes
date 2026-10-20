package org.ggomes.myleo.llm.onnxgenai.data

import ai.onnxruntime.genai.Config
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
 * Each `generate` call builds the MiniCPM5-1B prompt, encodes it, and streams
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
    @Volatile private var config: Config? = null
    @Volatile private var lastTokenCount = 0

    override suspend fun loadModel(): ChatModelInfo = withContext(Dispatchers.Default) {
        val existingModel = model
        val existingTokenizer = tokenizer
        if (existingModel != null && existingTokenizer != null) {
            return@withContext ChatModelInfo(File(modelDir).name, BACKEND, 0, sizeBytes, SOURCE)
        }
        val start = System.currentTimeMillis()
        var created: Model? = null
        var createdConfig: Config? = null
        try {
            // ORTX rejects MiniCPM5's `\p{N}+` Split regex; the patch is idempotent.
            TokenizerPatch.apply(File(modelDir, OnnxGenAIModelLocator.TOKENIZER_FILE))
            // The int4 package ships weights without a genai_config.json.
            MiniCPM5GenAIConfig.ensure(File(modelDir))
            createdConfig = Config(modelDir)
            created = Model(createdConfig)
            val createdTokenizer = Tokenizer(created)
            model = created
            tokenizer = createdTokenizer
            config = createdConfig
        } catch (e: Exception) {
            // Don't leave a half-initialized engine behind.
            model = null
            tokenizer = null
            config = null
            runCatching { created?.close() }
            runCatching { createdConfig?.close() }
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

        val prompt = MiniCPM5ChatTemplate.format(systemPrompt, turns)
        val sequences = tokenizer.encode(prompt)
        val promptTokens = sequences.getSequence(0).size
        val generatorParams = GeneratorParams(model).apply {
            setSearchOption("do_sample", true)
            setSearchOption("temperature", params.temperature)
            setSearchOption("top_k", params.topK.toDouble())
            setSearchOption("top_p", params.topP)
            // GenAI's max_length caps the whole sequence, prompt included.
            setSearchOption("max_length", maxSequenceLength(promptTokens, params.maxTokens).toDouble())
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
        runCatching { config?.close() }
        model = null
        tokenizer = null
        config = null
    }

    private companion object {
        const val BACKEND = "ONNX Runtime GenAI (CPU)"
        const val SOURCE = "https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4"
    }
}

/**
 * GenAI's `max_length` caps the **whole** sequence (prompt + output), while our
 * `maxTokens` slider means output tokens only — so the prompt is added back in.
 * The old `maxTokens + 1024` let a 512-token request run for ~1500 tokens.
 */
internal fun maxSequenceLength(promptTokens: Int, maxTokens: Int): Int =
    promptTokens + maxTokens
