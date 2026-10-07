import Foundation

/// Raw LiteRT inference seam. Production code uses `InterpreterRunner`; unit
/// tests use a fake. Keeps LiteRT classes out of unit-test logic.
protocol InferenceRunner {
    /// Runs one `[1 x seqLen]` Int32 triple and returns the raw embedding vector.
    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float]
}

/// `Embedder` over raw LiteRT: tokenize → run → L2-normalize (F-VS-2).
///
/// The output is always L2-normalized so cosine similarity reduces to a dot product.
struct LiteRTEmbedder: Embedder {
    let runner: InferenceRunner
    let tokenizer: TextTokenizer
    let maxSeqLen: Int

    static let defaultMaxSeqLen = 128

    init(runner: InferenceRunner, tokenizer: TextTokenizer, maxSeqLen: Int = defaultMaxSeqLen) {
        self.runner = runner
        self.tokenizer = tokenizer
        self.maxSeqLen = maxSeqLen
    }

    func embed(text: String) throws -> [Float] {
        let input = tokenizer.tokenize(text: text, maxSeqLen: maxSeqLen)
        return l2Normalize(try runner.runEmbedding(ids: input.ids, mask: input.mask, segmentIds: input.segmentIds))
    }
}

/// L2-normalizes `v`; a zero vector is returned unchanged (avoids divide-by-zero).
func l2Normalize(_ v: [Float]) -> [Float] {
    let norm = sqrt(v.reduce(0) { $0 + $1 * $1 })
    guard norm != 0 else { return v }
    return v.map { $0 / norm }
}
