# iOS Vector Search — Trio (LiteRT + ONNX + Core ML)

- Runtime: `TensorFlowLiteSwift` `~> 2.17.0` + `onnxruntime-objc` `~> 1.30.0` via CocoaPods, plus system Core ML — selectable at runtime, CPU (delegates/EPs later)
- Model format: same `all-MiniLM-L6-v2` weights in three files — `embedding.tflite` (Int32 inputs by tensor name, `[1 x 128 x 384]` sequence out, mean-pooled in code), `embedding.onnx` (Int64 in, `last_hidden_state` out), `embedding.mlpackage` (Int32 in incl. `position_ids`, compiled to `.mlmodelc` at build time) — plus one shared `vocab.txt`
- Scope contract:
  - Runtime picker first — no model loads until you choose (lazy init, cached per session)
  - Embed text → normalized float vector via the active runtime
  - Shared in-memory index with top-k cosine search — switching runtimes clears it (confirm dialog)
  - Report embed ms + search ms; SwiftUI demo: ingest (free text or Enron emails), query, ranked hits + email detail with query highlighting
- Models (gitignored, never committed): copy locally from the sibling leafs — `../litert/Sources/Resources/embedding.tflite`, `../onnx/Sources/Resources/embedding.onnx`, `../coreml/Sources/Resources/embedding.mlpackage/`, any one `vocab.txt` — into `Sources/Resources/`.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — copy the sample locally: `cp ../litert/Sources/Resources/enron_sample.csv Sources/Resources/enron_sample.csv`. Ingest screen → "Enron emails" source → choose item count.
- Sizes (measured): runtime pods add ~5 MB (LiteRT) + ~36 MB (ONNX); Core ML ships with iOS (0 MB); models add ~90 + ~90 + ~86 MB. Debug `.app` totals ~310 MB.
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoVSTrio.xcworkspace` (always the workspace). Tests: `xcodebuild -workspace MyLeoVSTrio.xcworkspace -scheme MyLeoVSTrio -destination 'platform=iOS Simulator,name=iPhone 17' test`. Device runs need signing overrides (`DEVELOPMENT_TEAM=...`) — never committed.
