import SwiftUI

/// Temporary shell while the decision pipeline is ported. It names the runtime and the model
/// contract so the scaffold is self-describing; the MVI screen replaces it once the engine and
/// downloader land.
struct DecisionPlaceholderView: View {
    let runtime: String
    let model: String

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text(runtime).font(.largeTitle.bold())
                Text(model).font(.subheadline).foregroundStyle(.secondary)
                Divider()
                Text("Julia-1 decision contract:")
                    .font(.headline)
                Text("state + typed question (choice / score / noul) + 2–20 options → one probability per option from a single forward pass. No token streaming, no chat.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                Text("Next: tokenizer, token table / ONNX inputs, deciders, suite report.")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(20)
            .navigationTitle("MyLeo Decision")
        }
    }
}
