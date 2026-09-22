import Foundation

enum EmbeddingUseCaseError: Error, Equatable {
    case emptyQuery
}

/// Calculates the embedding vector for a single input string (F-VS-2).
///
/// Throws `emptyQuery` for blank input without touching the repository.
struct CalculateEmbeddingUseCase {
    let repository: EmbeddingRepository

    func callAsFunction(_ text: String) throws -> [Float] {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw EmbeddingUseCaseError.emptyQuery
        }
        return try repository.embedText(text)
    }
}
