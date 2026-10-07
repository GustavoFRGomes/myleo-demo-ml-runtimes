package org.ggomes.myleolite.domain

import org.ggomes.myleolite.data.EmbeddingRepository

/**
 * Calculates the embedding vector for a single input string (F-VS-2).
 *
 * Returns `Result.failure` for blank input without touching the [EmbeddingRepository].
 */
class CalculateEmbeddingUseCase(
    private val repository: EmbeddingRepository,
) {
    operator fun invoke(text: String): Result<List<Float>> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("text must not be blank"))
        }
        return repository.embedText(text)
    }
}
