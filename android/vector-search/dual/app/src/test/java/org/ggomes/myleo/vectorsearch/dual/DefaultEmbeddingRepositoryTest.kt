package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class DefaultEmbeddingRepositoryTest {

    @Test
    fun embedText_delegatesToEmbedder() {
        val repo = DefaultEmbeddingRepository(FakeEmbedder(listOf(0.5f)))

        assertThat(repo.embedText("hi").getOrNull()).containsExactly(0.5f)
    }

    @Test
    fun embedText_embedderThrows_returnsFailure() {
        val repo = DefaultEmbeddingRepository(
            object : Embedder {
                override fun embed(text: String): List<Float> = throw RuntimeException("boom")
            },
        )

        val result = repo.embedText("hi")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessage("boom")
    }

    private class FakeEmbedder(private val vector: List<Float>) : Embedder {
        override fun embed(text: String): List<Float> = vector
    }
}
