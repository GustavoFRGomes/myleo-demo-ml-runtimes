import Foundation

/// The author's answer arithmetic turned the marker logits into these fields.
struct DecodedDecision: Equatable, Sendable {
    let probabilities: [Double]
    let argmax: Int
    let choiceId: String?
    let score: Double?
    let noul: Double?
    let maxProbability: Double?
}

/// Port of the Julia-1 named-question arithmetic (`julia/typed.py`): float64 softmax at
/// temperature 1, no calibration. Free of SDK types so the exact answer rules are unit-tested.
enum JuliaDecoder {

    /// Reads the option logits at the sequence builder's marker positions.
    static func gather(tokenLogits: [Float], markers: [Int]) throws -> [Double] {
        guard !tokenLogits.isEmpty else {
            throw DecisionError.graph("token_logits is empty")
        }
        return try markers.map { marker in
            guard tokenLogits.indices.contains(marker) else {
                throw DecisionError.graph("marker \(marker) outside token_logits")
            }
            return Double(tokenLogits[marker])
        }
    }

    /// `p_i = exp(z_i - max) / sum`, computed in float64 with a sequential sum.
    static func softmax(_ logits: [Double]) throws -> [Double] {
        guard !logits.isEmpty else {
            throw DecisionError.graph("at least one logit is required")
        }
        guard logits.allSatisfy({ $0.isFinite }) else {
            throw DecisionError.graph("nonfinite model scores")
        }
        let maximum = logits.max() ?? 0
        let exponents = logits.map { Foundation.exp($0 - maximum) }
        let total = exponents.reduce(0, +)
        guard total > 0 else { throw DecisionError.graph("softmax denominator is zero") }
        return exponents.map { $0 / total }
    }

    /// choice = winning option id, score = expected zero-based rubric index (not rounded),
    /// noul = P(true) — the second option, whose id the domain type pins to `true`.
    static func decode(markerLogits: [Double], question: DecisionQuestion) throws -> DecodedDecision {
        guard markerLogits.count == question.options.count else {
            throw DecisionError.graph("logits/option count mismatch")
        }
        let probabilities = try softmax(markerLogits)

        var argmax = 0
        for index in 1..<probabilities.count where probabilities[index] > probabilities[argmax] {
            argmax = index
        }
        let maxProbability = probabilities.max()

        switch question.type {
        case .choice:
            return DecodedDecision(
                probabilities: probabilities,
                argmax: argmax,
                choiceId: question.options[argmax].id,
                score: nil,
                noul: nil,
                maxProbability: maxProbability
            )
        case .score:
            var expectation = 0.0
            for (index, probability) in probabilities.enumerated() {
                expectation += Double(index) * probability
            }
            return DecodedDecision(
                probabilities: probabilities,
                argmax: argmax,
                choiceId: nil,
                score: expectation,
                noul: nil,
                maxProbability: maxProbability
            )
        case .noul:
            return DecodedDecision(
                probabilities: probabilities,
                argmax: argmax,
                choiceId: nil,
                score: nil,
                noul: probabilities[1],
                maxProbability: nil
            )
        }
    }
}
