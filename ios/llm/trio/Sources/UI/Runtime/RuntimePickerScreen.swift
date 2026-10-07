import SwiftUI

/// First screen: choose the LLM runtime. Models download on first open when
/// missing (the chat screen's status banner offers Download).
struct RuntimePickerScreen: View {
    @ObservedObject var session: RuntimeSession

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Choose a runtime. LiteRT-LM and ONNX GenAI share Phi-4-mini-instruct in that runtime's format; Core ML uses MiniCPM5-1B. Models download on first use if missing.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                ForEach(session.cards) { card in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(card.runtime.label).font(.headline)
                        Text(card.runtime.subtitle)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Text(card.modelPresent ? "Model present" : "Model not downloaded yet")
                            .font(.caption)
                            .foregroundStyle(card.modelPresent ? Color.secondary : Color.orange)
                        Button("Open \(card.runtime.label)") { session.select(card.runtime) }
                            .buttonStyle(.borderedProminent)
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("MyLeo LLM Trio")
            .onAppear { session.refreshCards() }
        }
    }
}
