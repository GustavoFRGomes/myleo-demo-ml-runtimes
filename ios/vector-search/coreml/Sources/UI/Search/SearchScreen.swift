import SwiftUI

struct SearchScreen: View {
    @ObservedObject var store: SearchStore
    var onOpenIngest: () -> Void
    var onShowStats: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    TextField("Search query", text: Binding(
                        get: { store.state.query },
                        set: { store.send(.queryChanged($0)) }
                    ))
                    .textFieldStyle(.roundedBorder)
                    Button("Search") { store.send(.search) }
                        .disabled(store.state.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            || store.state.isSearching)
                }
                if store.state.isSearching {
                    ProgressView()
                } else if let error = store.state.error {
                    Text(error).foregroundStyle(.red)
                } else if store.state.hits.isEmpty {
                    Text("Index is empty — tap the gear to ingest text first.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if let ms = store.state.queryMs {
                    Text("Ranked \(store.state.hits.count) hits in \(ms) ms")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                List(Array(store.state.hits.enumerated()), id: \.offset) { _, hit in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(hit.text)
                            .font(.headline)
                            .lineLimit(3)
                        Text("\(Int((hit.similarity * 100).rounded()))% match")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                .listStyle(.plain)
            }
            .padding()
            .navigationTitle("MyLeo VS Core ML")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { onOpenIngest() } label: {
                        Label("Prepare corpus", systemImage: "gear")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { onShowStats() } label: {
                        Label("Statistics", systemImage: "chart.bar")
                    }
                }
            }
        }
    }
}

#Preview {
    SearchScreen(
        store: SearchStore(
            useCase: CalculateEmbeddingUseCase(repository: PreviewRepositories.embedding),
            documents: PreviewRepositories.documents
        ),
        onOpenIngest: {},
        onShowStats: {}
    )
}
