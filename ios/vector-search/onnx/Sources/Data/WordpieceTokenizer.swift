import Foundation

/// BERT WordPiece tokenizer (basic tokenize + greedy longest-match-first).
struct WordpieceTokenizer: TextTokenizer {
    let vocab: [String]
    let doLowerCase: Bool
    let unkToken: String
    let clsToken: String
    let sepToken: String
    let padToken: String
    let maxInputCharsPerWord: Int

    private let vocabIndex: [String: Int]

    init(
        vocab: [String],
        doLowerCase: Bool = true,
        unkToken: String = "[UNK]",
        clsToken: String = "[CLS]",
        sepToken: String = "[SEP]",
        padToken: String = "[PAD]",
        maxInputCharsPerWord: Int = 100
    ) {
        precondition(vocab.contains(unkToken), "vocab must contain \(unkToken)")
        self.vocab = vocab
        self.doLowerCase = doLowerCase
        self.unkToken = unkToken
        self.clsToken = clsToken
        self.sepToken = sepToken
        self.padToken = padToken
        self.maxInputCharsPerWord = maxInputCharsPerWord
        var index: [String: Int] = [:]
        for (i, token) in vocab.enumerated() { index[token] = i }
        self.vocabIndex = index
    }

    private func tokenID(_ token: String) -> Int32 {
        Int32(vocabIndex[token] ?? vocabIndex[unkToken]!)
    }

    func tokenize(text: String, maxSeqLen: Int) -> TokenizedInput {
        precondition(maxSeqLen >= 2, "maxSeqLen must leave room for [CLS] and [SEP]")
        var ids = [tokenID(clsToken)]
        for word in basicTokenize(text) {
            if ids.count >= maxSeqLen - 1 { break } // reserve room for [SEP]
            ids += wordpieceTokenize(word)
        }
        let truncated = Array(ids.prefix(maxSeqLen - 1)) + [tokenID(sepToken)]
        let mask = (0..<maxSeqLen).map { Int32($0 < truncated.count ? 1 : 0) }
        let padded = truncated + Array(repeating: tokenID(padToken), count: maxSeqLen - truncated.count)
        return TokenizedInput(ids: padded, mask: mask, segmentIds: Array(repeating: 0, count: maxSeqLen))
    }

    func basicTokenize(_ text: String) -> [String] {
        let normalized = doLowerCase ? text.lowercased() : text
        // Space out CJK chars so each becomes its own token.
        var spaced = ""
        spaced.reserveCapacity(normalized.count)
        for scalar in normalized.unicodeScalars {
            if isCJK(scalar) {
                spaced.append(" ")
                spaced.unicodeScalars.append(scalar)
                spaced.append(" ")
            } else {
                spaced.unicodeScalars.append(scalar)
            }
        }
        // Split on whitespace, then split punctuation into single-char tokens.
        return spaced.split(whereSeparator: { $0.isWhitespace }).flatMap { splitPunctuation(String($0)) }
    }

    private func splitPunctuation(_ word: String) -> [String] {
        var out: [String] = []
        var current = ""
        for scalar in word.unicodeScalars {
            if isPunctuation(scalar) {
                if !current.isEmpty {
                    out.append(current)
                    current = ""
                }
                out.append(String(scalar))
            } else {
                current.unicodeScalars.append(scalar)
            }
        }
        if !current.isEmpty { out.append(current) }
        return out
    }

    func wordpieceTokenize(_ word: String) -> [Int32] {
        if word.count > maxInputCharsPerWord { return [tokenID(unkToken)] }
        let chars = Array(word)
        var ids: [Int32] = []
        var start = chars.startIndex
        while start < chars.endIndex {
            var end = chars.endIndex
            var found: Int32?
            while start < end {
                let piece = (start > chars.startIndex ? "##" : "") + String(chars[start..<end])
                if let id = vocabIndex[piece] {
                    found = Int32(id)
                    break
                }
                end = chars.index(before: end)
            }
            guard let id = found else { return [tokenID(unkToken)] }
            ids.append(id)
            start = end
        }
        return ids
    }

    private func isPunctuation(_ scalar: Unicode.Scalar) -> Bool {
        let v = scalar.value
        if (33...47).contains(v) || (58...64).contains(v) || (91...96).contains(v) || (123...126).contains(v) {
            return true
        }
        switch scalar.properties.generalCategory {
        case .connectorPunctuation, .dashPunctuation, .closePunctuation,
             .finalPunctuation, .initialPunctuation, .otherPunctuation, .openPunctuation:
            return true
        default:
            return false
        }
    }

    private func isCJK(_ scalar: Unicode.Scalar) -> Bool {
        let v = scalar.value
        return (0x4E00...0x9FFF).contains(v) || (0x3400...0x4DBF).contains(v) ||
            (0x20000...0x2A6DF).contains(v) || (0xF900...0xFAFF).contains(v)
    }
}
