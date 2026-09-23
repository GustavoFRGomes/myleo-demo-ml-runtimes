import Foundation

/// Fixed-length BERT-style model inputs for one text.
struct TokenizedInput: Equatable {
    let ids: [Int32]
    let mask: [Int32]
    let segmentIds: [Int32]
}

/// Converts raw text into model input IDs. Implementations own the vocabulary
/// (e.g. a WordPiece `vocab.txt` bundled in the app).
protocol TextTokenizer {
    func tokenize(text: String, maxSeqLen: Int) -> TokenizedInput
}
