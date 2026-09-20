# iOS Vector Search — Core ML

- Runtime: Core ML + Accelerate / BNNS (Neural Engine where available)
- Model format: `.mlpackage` sentence embedding
- Scope contract:
  - Embed text → normalized float vector via Core ML prediction
  - On-device index with top-k cosine search (Accelerate) + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Build/run: `xcodegen generate`, then `open MyLeoVSCoreML.xcodeproj` or `xcodebuild -scheme MyLeoVSCoreML -destination 'platform=iOS Simulator,name=iPhone 17' build`
