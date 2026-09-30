import SwiftUI

struct IngestScreen: View {
    @ObservedObject var store: IngestStore
    let runtime: InferenceRuntime
    var onBack: () -> Void

    private var canIngest: Bool {
        switch store.state.source {
        case .freeText:
            !store.state.corpusText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .enronSample:
            (store.state.availableItems ?? 0) > 0
        }
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                if let ms = store.state.modelLoadMs {
                    Text("Embedding model loaded in \(ms) ms")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Text("Index: \(store.state.corpus.documents) documents · \(store.state.corpus.chunks) chunks")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Picker("Source", selection: Binding(
                    get: { store.state.source },
                    set: { store.send(.sourceChanged($0)) }
                )) {
                    Text("Free text").tag(IngestSource.freeText)
                    Text("Enron emails").tag(IngestSource.enronSample)
                }
                .pickerStyle(.segmented)
                if store.state.source == .freeText {
                    TextEditor(text: Binding(
                        get: { store.state.corpusText },
                        set: { store.send(.corpusChanged($0)) }
                    ))
                    .frame(minHeight: 120)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary.opacity(0.4)))
                } else {
                    Stepper(
                        "Emails: \(store.state.itemCount)" + (store.state.availableItems.map { " of \($0)" } ?? ""),
                        value: Binding(
                            get: { store.state.itemCount },
                            set: { store.send(.itemCountChanged($0)) }
                        ),
                        in: 1...(store.state.availableItems ?? 300),
                        step: 10
                    )
                }
                HStack(spacing: 8) {
                    Button("Calculate embeddings") { store.send(.ingest) }
                        .buttonStyle(.borderedProminent)
                        .disabled(!canIngest || store.state.isIngesting)
                    Button("Clear index", role: .destructive) { store.send(.clearIndex) }
                        .disabled(store.state.corpus.chunks == 0 || store.state.isIngesting)
                }
                if store.state.isIngesting {
                    ProgressView()
                } else if let error = store.state.error {
                    Text(error).foregroundStyle(.red)
                } else if let run = store.state.lastRun {
                    Text("\(run.document): \(run.chunks) chunks in \(run.totalMs) ms (embed \(run.embedMs) ms, store \(run.storeMs) ms)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if store.state.stored {
                    Text("Saved \(store.state.chunks.count) chunks to the on-device index.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if store.state.chunks.isEmpty {
                    Text("No chunks yet — paste text and tap Calculate.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                List(Array(store.state.chunks.enumerated()), id: \.offset) { _, chunk in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(chunk.text)
                            .font(.headline)
                            .lineLimit(3)
                        Text("\(chunk.dimensions) dims · \(chunk.embedMs) ms")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                .listStyle(.plain)
            }
            .padding()
            .navigationTitle("Prepare corpus · \(runtime.label)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { onBack() } label: {
                        Label("Back", systemImage: "chevron.backward")
                    }
                }
            }
        }
    }
}

#Preview {
    IngestScreen(
        store: IngestStore(
            useCase: CalculateEmbeddingUseCase(repository: PreviewRepositories.embedding),
            documents: PreviewRepositories.documents
        ),
        runtime: .litert,
        onBack: {}
    )
}
