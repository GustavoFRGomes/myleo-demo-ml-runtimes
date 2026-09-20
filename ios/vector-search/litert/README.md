# iOS Vector Search — LiteRT

- Runtime: LiteRT (TF Lite successor), Swift API + GPU / Neural Engine delegates
- Model format: `.tflite` sentence embedding (e.g. Universal Sentence Encoder, MiniLM)
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Future build/run: open this folder's Xcode project / SPM package (not scaffolded yet)
