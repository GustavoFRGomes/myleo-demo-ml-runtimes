package org.ggomes.myleo.llm.dual.data

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ggomes.myleo.llm.dual.domain.ChatInference
import org.ggomes.myleo.llm.dual.domain.ChatMessage
import org.ggomes.myleo.llm.dual.domain.ChatModelInfo
import org.ggomes.myleo.llm.dual.domain.ChatRole
import org.ggomes.myleo.llm.dual.domain.GenerationParams

/**
 * [ChatInference] over LiteRT-LM's Kotlin `Engine`/`Conversation` API.
 *
 * Tries the **GPU** backend first and falls back to **CPU** when the device has no
 * usable OpenCL driver (a missing one surfaces as
 * "UNKNOWN: Can not find OpenCL library on this device"). The backend that actually
 * initialized is reported in [ChatModelInfo.backend] and shown in the info sheet.
 * The NPU backend is intentionally not used: it needs Qualcomm QNN libraries bundled
 * in the app plus a `.litertlm` built for the specific SoC (see README).
 *
 * GPU is skipped up front when the device is already known to be unable to hold it:
 * [GpuPolicy] supplies total RAM and the remembered verdict and [GpuPreflight] decides
 * (see [planBackends]). A GPU attempt that is killed for memory leaves
 * [GpuVerdict.PENDING] behind, which the next run resolves to [GpuVerdict.BAD] — the
 * only way to catch an OOM kill, since it is a `SIGKILL` no `try/catch` can observe.
 *
 * Each `generate` call builds a fresh conversation from the full history and
 * streams the reply (`Conversation.sendMessageAsync` → `Flow<Message>`).
 * Rebuilding per turn keeps regenerate/clear trivial; a KV-cache-reusing
 * conversation can replace this behind the same interface.
 */
class LiteRTLMEngine(
    private val modelPath: String,
    private val gpuPolicy: GpuPolicy = GpuPolicy.Allow,
) : ChatInference, AutoCloseable {

    @Volatile private var engine: Engine? = null
    @Volatile private var activeBackend: Backend? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var lastTokenCount = 0
    @Volatile private var maxContextTokens = 0

    override suspend fun loadModel(): ChatModelInfo = withContext(Dispatchers.Default) {
        engine?.let { return@withContext infoFor(modelPath) }
        val start = System.currentTimeMillis()
        val backends = planBackends(File(modelPath).length())
        val (created, used) = firstWorkingBackend(backends, onFailure = ::logBackendFailure) { backend ->
            val gpu = backend is Backend.GPU
            // Persist *before* the native init: a memory kill here leaves PENDING behind,
            // the only trace the in-process fallback below can never see.
            if (gpu) gpuPolicy.record(GpuVerdict.PENDING)
            val candidate = Engine(EngineConfig(modelPath = modelPath, backend = backend))
            try {
                candidate.initialize()
                if (gpu) gpuPolicy.record(GpuVerdict.OK)
                candidate
            } catch (e: Exception) {
                if (gpu) gpuPolicy.record(GpuVerdict.BAD)
                // Don't leak a half-initialized engine before trying the next backend.
                runCatching { candidate.close() }
                throw e
            }
        }
        engine = created
        activeBackend = used
        maxContextTokens = contextLength(modelPath)
        val ms = System.currentTimeMillis() - start
        ChatModelInfo(
            displayName = File(modelPath).name,
            backend = backendName(used),
            loadMs = ms,
            sizeBytes = File(modelPath).length(),
            sourceUrl = null,
            maxContextTokens = maxContextTokens,
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
        backend = activeBackend?.let(::backendName) ?: "unknown",
        loadMs = 0,
        sizeBytes = File(path).length(),
        sourceUrl = null,
        maxContextTokens = maxContextTokens,
    )

    /**
     * Reads the model's metadata without loading the weights (LiteRT-LM 0.18.0),
     * so the info sheet can show the context length. Returns 0 when the bundle
     * reports none — the field is informational, never a load precondition.
     */
    private fun contextLength(path: String): Int =
        runCatching { ModelInfo.from(path).use { it.maxContextTokens() } }.getOrDefault(0)

    /**
     * Backends to try, in order. GPU first unless we already know it can't work here:
     * a remembered bad verdict, or a device whose RAM can't hold the GPU weight copy.
     */
    private fun planBackends(modelBytes: Long): List<Backend> {
        val verdict = resolveStoredVerdict()
        val totalRam = gpuPolicy.totalRamBytes()
        if (!GpuPreflight.canAttemptGpu(modelBytes, totalRam, verdict)) {
            Log.i(TAG, gpuSkipReason(verdict, modelBytes, totalRam))
            return listOf(Backend.CPU())
        }
        return listOf(Backend.GPU(), Backend.CPU())
    }

    /** Reads the remembered verdict, folding a leftover [GpuVerdict.PENDING] into BAD. */
    private fun resolveStoredVerdict(): GpuVerdict {
        val stored = gpuPolicy.verdict()
        val verdict = stored.resolved()
        if (verdict != stored) {
            Log.w(TAG, "previous GPU attempt was killed mid-load; disabling GPU")
            gpuPolicy.record(verdict)
        }
        return verdict
    }

    private fun gpuSkipReason(verdict: GpuVerdict, modelBytes: Long, totalRamBytes: Long): String =
        if (verdict == GpuVerdict.BAD) {
            "GPU disabled after a previous failed attempt; using CPU"
        } else {
            "GPU skipped: needs ~${GpuPreflight.requiredBytes(modelBytes) / MB} MB RAM, " +
                "device has ${totalRamBytes / MB} MB"
        }

    private fun logBackendFailure(backend: Backend, error: Throwable) {
        Log.w(TAG, "LiteRT-LM ${backendName(backend)} backend unavailable; falling back", error)
    }

    private fun backendName(backend: Backend): String = when (backend) {
        is Backend.GPU -> "GPU"
        is Backend.CPU -> "CPU"
        is Backend.NPU -> "NPU"
        else -> backend.name
    }

    companion object {
        private const val TAG = "MyLeo-LiteRTLM"
        private const val MB = 1024L * 1024
    }
}
