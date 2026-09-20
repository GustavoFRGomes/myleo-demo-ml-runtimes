# iOS Vector Search — ONNX

- Runtime: ONNX Runtime Mobile (Swift/Obj-C API)
- Model format: `.onnx` embedding model + tokenizer (e.g. MiniLM, E5-small)
- Scope contract:
  - Tokenize + embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Build/run: `xcodegen generate`, then `open MyLeoVSOnnx.xcodeproj` or `xcodebuild -scheme MyLeoVSOnnx -destination 'platform=iOS Simulator,name=iPhone 17' build`
