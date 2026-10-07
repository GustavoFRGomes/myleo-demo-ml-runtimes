package org.ggomes.myleo.vectorsearch.onnx

import org.ggomes.myleo.vectorsearch.onnx.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.onnx.domain.Embedder
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.assertj.core.api.Assertions.assertThat

class DefaultEmbeddingRepositoryTest {

    @Test
    fun embedText_validInput_returnsEmbedderVector() {
        val embedder = mock<Embedder>()
        whenever(embedder.embed("hello")).thenReturn(listOf(0.5f, -0.5f))
        val repository = DefaultEmbeddingRepository(embedder)

        val result = repository.embedText("hello")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly(0.5f, -0.5f)
        verify(embedder).embed("hello")
    }

    @Test
    fun embedText_embedderThrows_returnsFailure() {
        val embedder = mock<Embedder>()
        whenever(embedder.embed("boom")).thenThrow(IllegalStateException("no model loaded"))
        val repository = DefaultEmbeddingRepository(embedder)

        val result = repository.embedText("boom")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }
}
