import SwiftUI

/// One form for every decision runtime: runtime picker, state, typed question, options, and the
/// per-option probabilities with per-stage timings.
struct DecisionScreen: View {
    @StateObject private var session = DecisionSession()

    var body: some View {
        NavigationStack {
            Form {
                Section("Runtime") {
                    Picker("Runtime", selection: Binding(
                        get: { session.runtime },
                        set: { session.select($0) }
                    )) {
                        Text("LiteRT").tag(DecisionRuntime.litert)
                        Text("ONNX Runtime").tag(DecisionRuntime.onnx)
                        Text("Core ML (soon)").tag(DecisionRuntime.coreML)
                    }
                    .pickerStyle(.segmented)
                    if session.runtime == .coreML {
                        Text(DecisionRuntime.coreML.unavailableReason ?? "")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    } else {
                        Text(session.runtime.subtitle).font(.caption).foregroundStyle(.secondary)
                    }
                }

                Section("State") {
                    TextEditor(text: $session.stateText)
                        .frame(minHeight: 80)
                }

                Section("Question") {
                    Picker("Type", selection: $session.questionType) {
                        Text("choice").tag(QuestionType.choice)
                        Text("score").tag(QuestionType.score)
                        Text("noul").tag(QuestionType.noul)
                    }
                    .pickerStyle(.segmented)
                    ForEach(session.options.indices, id: \.self) { index in
                        TextField("Option \(index + 1)", text: Binding(
                            get: { session.options[index] },
                            set: { session.options[index] = $0 }
                        ))
                    }
                    if session.questionType != .noul {
                        HStack {
                            Button("Add option") {
                                if session.options.count < 20 { session.options.append("") }
                            }
                            Spacer()
                            Button("Remove", role: .destructive) {
                                if session.options.count > 2 { session.options.removeLast() }
                            }
                        }
                        .font(.footnote)
                    }
                }

                Section {
                    Button {
                        Task { await session.run() }
                    } label: {
                        HStack {
                            Text(session.busy ? "Deciding…" : "Decide")
                            Spacer()
                            if session.busy { ProgressView() }
                        }
                    }
                    .disabled(!session.canRun)
                }

                if let error = session.error {
                    Section { Text(error).foregroundStyle(.red).font(.footnote) }
                }

                if let outcome = session.outcome {
                    resultSection(outcome)
                }

                Section("About the lanes") {
                    Text("LiteRT runs on the CPU here: the TFLite Metal and Core ML delegates cannot compile this graph, and the LiteRT 2.3 Metal accelerator does not register on iOS.")
                    Text("ONNX Runtime is CPU-only by design and is the fastest lane on this phone; its export keeps the token table in-graph.")
                    Text("Requests that do not fit the window are rejected, never truncated.")
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
            .navigationTitle("MyLeo Decision Trio")
        }
    }

    @ViewBuilder
    private func resultSection(_ outcome: DecisionOutcome) -> some View {
        Section("Answer") {
            if let choice = outcome.choiceId {
                Text("choice: \(choice)").font(.headline)
            }
            if let score = outcome.score {
                Text(String(format: "score: %.3f", score)).font(.headline)
            }
            if let noul = outcome.noul {
                Text(String(format: "P(true): %.3f", noul)).font(.headline)
            }
            ForEach(Array(outcome.question.options.enumerated()), id: \.offset) { index, option in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(option.text).font(.caption)
                        Spacer()
                        Text(String(format: "%.4f", outcome.probabilities[index]))
                            .font(.caption).monospacedDigit()
                    }
                    ProgressView(value: outcome.probabilities[index])
                        .tint(index == outcome.argmax ? .accentColor : .gray)
                }
            }
        }
        Section("Timing") {
            let t = outcome.timings
            Text(String(format: "prepare %.2f ms · lookup %.2f ms · graph %.2f ms · decode %.2f ms",
                        t.prepareMs, t.lookupMs, t.graphMs, t.decodeMs))
                .font(.caption).monospacedDigit()
            Text("backend: \(outcome.accelerator.rawValue) · window \(outcome.window) · \(outcome.tokenCount) tokens")
                .font(.caption2).foregroundStyle(.secondary)
            if let info = session.info {
                Text(String(format: "load %.0f ms (tokenizer %.0f · graph %.0f)",
                            info.totalLoadMs, info.tokenizerLoadMs, info.graphLoadMs))
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}
