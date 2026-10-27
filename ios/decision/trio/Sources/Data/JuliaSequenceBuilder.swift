import Foundation

/// Tokenizer seam: the strict encoder only needs text → ids, so the graph implementation can
/// use the Swift Transformers tokenizer while tests use a synthetic encoder.
protocol JuliaEncoding: Sendable {
    func encode(_ text: String) -> [Int]
}

/// Unpadded token ids plus one marker position per option, for one graph invocation.
struct JuliaSequence: Equatable, Sendable {
    let ids: [Int]
    let markers: [Int]
    let qtype: Int

    var tokenCount: Int { ids.count }
}

/// Builds one request row exactly like the author's strict encoding:
///
/// ```
/// <bos> "{type} question: {instructions}" <eos>
///   [ <mask> option-text ] * N
///   <eos> state <eos>
/// ```
///
/// The logit at each `<mask>` scores the option text that follows it. Nothing is truncated:
/// a request that does not fit the window throws instead of silently losing part of the
/// question, an option or the state.
struct JuliaSequenceBuilder: Sendable {
    static let pad = 0
    static let sep = 1
    static let cls = 2
    static let mask = 4
    static let maxOptionTokens = 48
    private static let maskLiteral = "<mask>"

    let encoder: JuliaEncoding
    let window: Int
    /// Question + options budget; below `window - 4` keeps the author's strict rules.
    let headLength: Int

    init(encoder: JuliaEncoding, window: Int = 512, headLength: Int? = nil) throws {
        let head = headLength ?? min(512, window - 5)
        guard window > 0, head > 0, head < window - 4 else {
            throw DecisionError.encoding("invalid window or head budget")
        }
        self.encoder = encoder
        self.window = window
        self.headLength = head
    }

    func build(state: String, question: DecisionQuestion) throws -> JuliaSequence {
        let texts = [state, question.instructions] + question.options.map(\.text)
        guard !texts.contains(where: { $0.contains(Self.maskLiteral) }) else {
            throw DecisionError.encoding("reserved model marker in request")
        }

        let head = encoder.encode("\(question.type.rawValue) question: \(question.instructions)")
        let optionIds = question.options.map { encoder.encode(" \($0.text)") }

        if optionIds.contains(where: { $0.count > Self.maxOptionTokens }) {
            throw DecisionError.encoding("option exceeds the \(Self.maxOptionTokens)-token model contract")
        }

        let optionsCost = optionIds.reduce(0) { $0 + $1.count + 1 }
        let budget = headLength - optionsCost
        if budget < 16 {
            // The author's squeeze would cut options here; strict encoding rejects instead.
            let perOption = max(4, (headLength - 16) / max(optionIds.count, 1))
            if optionIds.contains(where: { $0.count + 1 > perOption }) {
                throw DecisionError.encoding("options exceed the head budget")
            }
        }
        if head.count > budget {
            throw DecisionError.encoding("question and options exceed the head budget")
        }

        var ids: [Int] = []
        ids.reserveCapacity(window)
        ids.append(Self.cls)
        ids.append(contentsOf: head)
        ids.append(Self.sep)

        var markers: [Int] = []
        for option in optionIds {
            markers.append(ids.count)
            ids.append(Self.mask)
            ids.append(contentsOf: option)
        }
        ids.append(Self.sep)

        let stateIds = encoder.encode(state)
        let needed = ids.count + stateIds.count + 1
        guard needed <= window else {
            throw DecisionError.encoding("request needs \(needed) tokens; the window is \(window)")
        }
        ids.append(contentsOf: stateIds)
        ids.append(Self.sep)

        return JuliaSequence(ids: ids, markers: markers, qtype: question.type.qtype)
    }
}
