import Foundation

/// One ranked row in the results list. `similarity` is cosine similarity in 0...1.
/// `source` is the full parent item (email raw string) the chunk came from.
struct SearchHit: Equatable {
    let text: String
    let similarity: Double
    let source: String
}

/// Screen state (MVI). Rendered by the view, never mutated there.
struct SearchState: Equatable {
    var query: String = ""
    var isSearching: Bool = false
    var hits: [SearchHit] = []
    var queryMs: Int64?
    var error: String?
}

/// User intents (MVI). The view sends these, the store reduces them.
enum SearchIntent {
    case queryChanged(String)
    case search
}
