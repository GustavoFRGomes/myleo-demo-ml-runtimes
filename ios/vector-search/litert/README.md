# iOS Vector Search — LiteRT

- Runtime: LiteRT (TF Lite successor), Swift API + GPU / Neural Engine delegates
- Model format: `.tflite` sentence embedding (e.g. Universal Sentence Encoder, MiniLM)
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Build/run: `xcodegen generate`, then `open MyLeoLite.xcodeproj` or `xcodebuild -scheme MyLeoLite -destination 'platform=iOS Simulator,name=iPhone 17' build`
