import SwiftUI

/// First screen: choose the embedding runtime. Nothing loads until a runtime
/// is tapped — init happens on demand with progress, failures stay on this
/// screen as an error with the other runtimes still selectable.
struct RuntimePickerScreen: View {
    @ObservedObject var session: RuntimeSession

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Choose an embedding runtime. The model loads only after you choose.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                ForEach(session.cards) { card in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(card.runtime.label).font(.headline)
                        Text(card.runtime.description)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Text(statusLine(card))
                            .font(.footnote)
                            .foregroundColor(card.bundled ? .secondary : .red)
                        Button(card.loaded ? "Use \(card.runtime.label) again" : "Load \(card.runtime.label)") {
                            session.select(card.runtime)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(session.isLoading || !card.bundled)
                        if session.isLoading, session.loadingRuntime == card.runtime {
                            ProgressView()
                        }
                    }
                    .padding(.vertical, 4)
                }
                if let error = session.error {
                    Section {
                        Text(error).foregroundStyle(.red)
                    }
                }
                Section {
                    Text("Switching runtimes later clears the shared index.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("MyLeo Trio")
        }
    }

    private func statusLine(_ card: RuntimeCard) -> String {
        if let ms = card.loadMs {
            "Loaded in \(ms) ms"
        } else if card.loaded {
            "Loaded"
        } else if card.bundled {
            "Model bundled — tap to load"
        } else {
            "Model not bundled — see README"
        }
    }
}

#Preview {
    RuntimePickerScreen(session: RuntimeSession(
        manager: RuntimeManager(documents: PreviewRepositories.documents),
        systemStats: UnknownSystemStats()
    ))
}
