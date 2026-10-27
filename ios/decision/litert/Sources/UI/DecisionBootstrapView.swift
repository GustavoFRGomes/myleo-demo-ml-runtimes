import SwiftUI

/// Bootstrap screen for the LiteRT leaf: reports what is installed and proves the tokenizer
/// loads and encodes. The decision form replaces this once the graph runner lands.
struct DecisionBootstrapView: View {
    @State private var status = "Checking package…"
    @State private var sample = ""
    @State private var detail = ""

    private let files = JuliaModelLocator.locate()
    private let tokenizerDirectory = JuliaModelLocator.tokenizerDirectory()

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text(status).font(.headline)
                if !detail.isEmpty {
                    Text(detail).font(.footnote).foregroundStyle(.secondary)
                }
                if !sample.isEmpty {
                    Text("choice question: Pick one →").font(.caption).foregroundStyle(.secondary)
                    Text(sample).font(.system(.caption, design: .monospaced))
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .navigationTitle("MyLeo Decision")
            .task { await check() }
        }
    }

    private func check() async {
        guard let tokenizerDirectory else {
            status = "Tokenizer files not installed"
            detail = "Expected \(JuliaModelLocator.directory().path)"
            return
        }
        status = "Loading tokenizer…"
        do {
            let tokenizer = try await JuliaTokenizer(directory: tokenizerDirectory)
            let ids = tokenizer.encode("choice question: Pick one")
            sample = ids.prefix(12).map(String.init).joined(separator: " ")
            detail = files.map { "windows \($0.windows.map(String.init).joined(separator: ", ")) · full package" } ?? "tokenizer only · graph/table still missing"
            status = "Tokenizer ready (\(ids.count) ids for the sample)"
            print("DECISION|tokenizer|ok|ids=\(ids.count)")
        } catch {
            status = "Tokenizer failed"
            detail = error.localizedDescription
            print("DECISION|tokenizer|error|\(error.localizedDescription)")
        }
    }
}
