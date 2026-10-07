import SwiftUI

/// Drill-down from a search hit: the full source item (email raw string) with
/// the query terms highlighted plus the match count.
struct ResultDetailScreen: View {
    let hit: SearchHit
    let query: String
    var onBack: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(matchCount) \(matchCount == 1 ? "match" : "matches") for \"\(query)\"")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(highlighted)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .navigationTitle("Email")
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

    private var highlighted: AttributedString {
        highlightMatches(in: hit.source, query: query).0
    }

    private var matchCount: Int {
        highlightMatches(in: hit.source, query: query).1
    }
}

#Preview {
    ResultDetailScreen(
        hit: SearchHit(text: "hello world", similarity: 0.87, source: "arnold-j hello world email body"),
        query: "hello",
        onBack: {}
    )
}
