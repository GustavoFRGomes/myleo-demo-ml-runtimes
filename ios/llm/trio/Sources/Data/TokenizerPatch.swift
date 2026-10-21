import Foundation

/// Repairs the one ORTX-incompatible regex in MiniCPM5-1B's `tokenizer.json`.
///
/// ONNX Runtime GenAI delegates BPE pre-tokenization to ORTX, which compiles every
/// `Split` regex against a fixed menu of alternation branches
/// (`operators/tokenizer/bpe_utils.hpp`). MiniCPM5's second `Split` contains
/// `\p{N}+`, which is not in that menu, so the leftover is compiled with
/// `std::regex` and the model refuses to load with `Invalid regex: \p{N}+`.
/// Replacing `+` with the equivalent bounded `{1,3}` (+4 bytes on disk) keeps both
/// `Split` steps and every token id intact — verified on Android against the
/// pristine HF tokenizer over 31 realistic and 20,000 fuzzed inputs.
///
/// The pattern is stored JSON-escaped, so the anchor is the two-backslash byte
/// sequence `\\p{N}+`. Idempotent: a file without the anchor is left untouched.
enum TokenizerPatch {
    /// JSON-escaped form exactly as it appears on disk.
    static let unsupported = Data(#"\\p{N}+"#.utf8)
    static let supported = Data(#"\\p{N}{1,3}"#.utf8)

    enum Result: Equatable {
        /// The anchor was present and has been rewritten.
        case patched
        /// No anchor left — already patched, or an export that never had it.
        case alreadyPatched
    }

    @discardableResult
    static func apply(to tokenizerFile: URL) throws -> Result {
        var data = try Data(contentsOf: tokenizerFile)
        var output = Data()
        var cursor = data.startIndex
        var didPatch = false

        while let range = data.range(of: unsupported, in: cursor..<data.endIndex) {
            output.append(data[cursor..<range.lowerBound])
            output.append(supported)
            cursor = range.upperBound
            didPatch = true
        }
        guard didPatch else { return .alreadyPatched }

        output.append(data[cursor..<data.endIndex])
        try output.write(to: tokenizerFile, options: .atomic)
        data.removeAll()
        return .patched
    }
}
