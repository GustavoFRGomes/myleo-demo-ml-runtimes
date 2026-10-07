package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.dual.data.EmbeddingRepository
import org.ggomes.myleo.vectorsearch.dual.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.assertj.core.api.Assertions.assertThat

class CalculateEmbeddingUseCaseTest {

    private lateinit var repository: FakeEmbeddingRepository
    private lateinit var useCase: CalculateEmbeddingUseCase

    @Before
    fun setUp() {
        repository = FakeEmbeddingRepository()
        useCase = CalculateEmbeddingUseCase(repository)
    }

    @Test
    fun invoke_blankText_returnsFailure() {
        val result = useCase("   ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun invoke_blankText_doesNotCallRepository() {
        val spyRepository = mock<EmbeddingRepository>()

        CalculateEmbeddingUseCase(spyRepository)("")

        verify(spyRepository, never()).embedText(any())
    }

    @Test
    fun invoke_validText_returnsRepositoryVector() {
        repository.vector = listOf(0.1f, 0.2f, 0.3f)

        val result = useCase("hello")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly(0.1f, 0.2f, 0.3f)
        assertThat(repository.lastInput).isEqualTo("hello")
    }

    @Test
    fun invoke_repositoryFailure_propagatesFailure() {
        repository.error = RuntimeException("engine exploded")

        val result = useCase("hello")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessage("engine exploded")
    }

    @Test
    fun invoke_validText_delegatesToEmbedder() {
        val embedder = mock<Embedder>()
        whenever(embedder.embed("hi")).thenReturn(listOf(1.0f))
        val repo = DefaultEmbeddingRepository(embedder)
        val useCase = CalculateEmbeddingUseCase(repo)

        val result = useCase("hi")

        assertThat(result.getOrNull()).containsExactly(1.0f)
        verify(embedder).embed("hi")
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        var vector: List<Float> = emptyList()
        var error: Throwable? = null
        var lastInput: String? = null

        override fun embedText(text: String): Result<List<Float>> {
            lastInput = text
            return error?.let { Result.failure(it) } ?: Result.success(vector)
        }
    }
}
