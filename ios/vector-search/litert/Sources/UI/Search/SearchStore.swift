import Combine
import Foundation
import os

/// Search screen store (MVI): embeds the query (F-VS-2) then ranks indexed
/// chunks (F-VS-4). Heavy work runs off the main actor; state publishes on it.
@MainActor
final class SearchStore: ObservableObject {
    @Published private(set) var state = SearchState()

    private let useCase: CalculateEmbeddingUseCase
    private let documents: DocumentRepository
    private let log = Logger(subsystem: "org.ggomes.myleolite", category: "Search")

    init(useCase: CalculateEmbeddingUseCase, documents: DocumentRepository) {
        self.useCase = useCase
        self.documents = documents
    }

    func send(_ intent: SearchIntent) {
        switch intent {
        case .queryChanged(let query):
            state.query = query
            state.error = nil
        case .search:
            let query = state.query
            guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !state.isSearching else { return }
            state.isSearching = true
            state.error = nil
            Task.detached { [weak self] in
                await self?.runSearch(query: query)
            }
        }
    }

    private func runSearch(query: String) async {
        var hits: [SearchHit]?
        var failure: String?
        var embedMs: Int64 = 0
        var searchMs: Int64 = 0
        do {
            let start = Date()
            let vector = try useCase(query)
            embedMs = ms(since: start)
            let searchStart = Date()
            do {
                let found = try documents.search(vector: vector)
                hits = found.map { SearchHit(text: $0.text, similarity: $0.similarity) }
            } catch {
                failure = error.localizedDescription
            }
            searchMs = ms(since: searchStart)
        } catch {
            failure = error.localizedDescription
        }
        let totalMs = embedMs + searchMs
        await MainActor.run {
            state.isSearching = false
            if let hits { state.hits = hits }
            if hits != nil { state.queryMs = totalMs }
            state.error = failure
        }
        log.debug("search query embedded in \(embedMs)ms, \(hits?.count ?? 0) hits ranked in \(searchMs)ms")
    }
}

private func ms(since date: Date) -> Int64 {
    Int64(Date().timeIntervalSince(date) * 1000)
}
