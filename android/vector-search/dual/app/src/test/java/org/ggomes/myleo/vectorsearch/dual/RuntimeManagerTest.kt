package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.ChunkEmbedding
import org.ggomes.myleo.vectorsearch.dual.data.CorpusStats
import org.ggomes.myleo.vectorsearch.dual.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.dual.data.DocumentStat
import org.ggomes.myleo.vectorsearch.dual.data.LoadedRuntime
import org.ggomes.myleo.vectorsearch.dual.data.RuntimeManager
import org.ggomes.myleo.vectorsearch.dual.data.ScoredChunk
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class RuntimeManagerTest {

    @Test
    fun select_firstUse_loadsAndSetsCurrent() {
        val manager = manager(loadCounts = mutableMapOf())

        val result = manager.select(InferenceRuntime.LITERT)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.modelName).isEqualTo("embedding.tflite")
        assertThat(manager.current).isEqualTo(InferenceRuntime.LITERT)
        assertThat(manager.useCase().isSuccess).isTrue()
    }

    @Test
    fun select_twice_loadsOnlyOnce() {
        val counts = mutableMapOf<InferenceRuntime, Int>()
        val manager = manager(loadCounts = counts)

        manager.select(InferenceRuntime.ONNX)
        manager.select(InferenceRuntime.ONNX)

        assertThat(counts[InferenceRuntime.ONNX]).isEqualTo(1)
    }

    @Test
    fun select_differentRuntime_clearsSharedIndex() {
        val documents = FakeDocumentRepository()
        val manager = RuntimeManager(documents, load = { FakeLoaded(it) })
        manager.select(InferenceRuntime.LITERT)
        documents.ingested["Document 1"] = listOf(ChunkEmbedding("a", listOf(1f)))

        manager.select(InferenceRuntime.ONNX)

        assertThat(manager.current).isEqualTo(InferenceRuntime.ONNX)
        assertThat(documents.clears).isEqualTo(1)
        assertThat(documents.ingested).isEmpty()
    }

    @Test
    fun select_sameRuntime_doesNotClearIndex() {
        val documents = FakeDocumentRepository()
        val manager = RuntimeManager(documents, load = { FakeLoaded(it) })
        manager.select(InferenceRuntime.LITERT)
        documents.ingested["Document 1"] = listOf(ChunkEmbedding("a", listOf(1f)))

        manager.select(InferenceRuntime.LITERT)

        assertThat(documents.clears).isEqualTo(0)
        assertThat(documents.ingested).isNotEmpty()
    }

    @Test
    fun select_loadFailure_keepsPreviousSelection() {
        val documents = FakeDocumentRepository()
        val manager = RuntimeManager(
            documents,
            load = { runtime ->
                if (runtime == InferenceRuntime.ONNX) throw IllegalStateException("no model")
                FakeLoaded(runtime)
            },
        )
        manager.select(InferenceRuntime.LITERT)

        val result = manager.select(InferenceRuntime.ONNX)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessage("no model")
        assertThat(manager.current).isEqualTo(InferenceRuntime.LITERT)
        assertThat(documents.clears).isEqualTo(0)
    }

    @Test
    fun useCase_beforeSelection_returnsFailure() {
        val manager = RuntimeManager(FakeDocumentRepository(), load = { FakeLoaded(it) })

        assertThat(manager.useCase().isFailure).isTrue()
    }

    private fun manager(loadCounts: MutableMap<InferenceRuntime, Int>): RuntimeManager =
        RuntimeManager(FakeDocumentRepository(), load = {
            loadCounts[it] = (loadCounts[it] ?: 0) + 1
            FakeLoaded(it)
        })

    private fun FakeLoaded(runtime: InferenceRuntime) = LoadedRuntime(
        embedder = object : Embedder {
            override fun embed(text: String): List<Float> = listOf(0.6f, 0.8f)
        },
        modelName = if (runtime == InferenceRuntime.LITERT) "embedding.tflite" else "embedding.onnx",
        modelLoadMs = 7,
    )

    private class FakeDocumentRepository : DocumentRepository {
        val ingested = mutableMapOf<String, List<ChunkEmbedding>>()
        var clears = 0

        override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> {
            ingested[document] = chunks
            return Result.success(Unit)
        }

        override fun clear(): Result<Unit> {
            clears++
            ingested.clear()
            return Result.success(Unit)
        }

        override fun stats(): Result<CorpusStats> =
            Result.success(CorpusStats(ingested.size, ingested.values.sumOf { it.size }))

        override fun documentStats(): Result<List<DocumentStat>> =
            Result.success(
                ingested.map { (label, chunks) -> DocumentStat(label, chunks.size) }
                    .sortedBy { it.label },
            )

        override fun search(vector: List<Float>, topK: Int): Result<List<ScoredChunk>> =
            Result.success(emptyList())
    }
}
