import Foundation

/// Builds the production embedding stack from the app bundle (model + vocab
/// bundled with the app — never needed in unit tests).
///
/// Expected bundle resources:
/// - `embedding.onnx` — MiniLM-style sentence model (Int64 `input_ids` /
///   `attention_mask` / `token_type_ids` in, float `last_hidden_state` out).
/// - `vocab.txt` — one WordPiece token per line (same vocabulary as BERT).
enum OnnxEmbedderFactory {
    static let defaultModelName = "embedding"
    static let defaultVocabName = "vocab"
    static let defaultModelFileName = "embedding.onnx"

    static func create(
        bundle: Bundle = .main,
        modelName: String = defaultModelName,
        vocabName: String = defaultVocabName,
        maxSeqLen: Int = LiteRTEmbedder.defaultMaxSeqLen
    ) throws -> LiteRTEmbedder {
        guard let modelPath = bundle.path(forResource: modelName, ofType: "onnx") else {
            throw OnnxEmbedderError.missingModel("\(modelName).onnx")
        }
        let env = try ORTEnv(loggingLevel: .warning)
        let session = try ORTSession(env: env, modelPath: modelPath, sessionOptions: nil)
        guard let vocabPath = bundle.path(forResource: vocabName, ofType: "txt"),
              let vocabText = try? String(contentsOfFile: vocabPath, encoding: .utf8)
        else {
            throw OnnxEmbedderError.missingVocab("\(vocabName).txt")
        }
        let vocab = vocabText.components(separatedBy: .newlines).filter { !$0.isEmpty }
        return LiteRTEmbedder(
            runner: OnnxRunner(session: session),
            tokenizer: WordpieceTokenizer(vocab: vocab),
            maxSeqLen: maxSeqLen
        )
    }
}
