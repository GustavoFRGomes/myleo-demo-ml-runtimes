import Foundation
import Tokenizers

/// `JuliaEncoding` over swift-transformers. `encode` matches the Julia host: **no special
/// tokens are added**, because `JuliaSequenceBuilder` places `<bos>`/`<eos>`/`<mask>` itself.
final class JuliaTokenizer: JuliaEncoding, @unchecked Sendable {
    private let tokenizer: any Tokenizer

    init(directory: URL) async throws {
        do {
            tokenizer = try await AutoTokenizer.from(modelFolder: directory)
        } catch {
            throw DecisionError.model("tokenizer: \(error.localizedDescription)")
        }
    }

    func encode(_ text: String) -> [Int] {
        tokenizer.encode(text: text, addSpecialTokens: false)
    }
}
