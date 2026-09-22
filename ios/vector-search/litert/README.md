# iOS Vector Search — LiteRT

- Runtime: `TensorFlowLiteSwift` `~> 2.17.0` via CocoaPods (raw `Interpreter` API, CPU; GPU/Core ML delegates later)
- Model format: `.tflite` BERT WordPiece embedding (INT32 `ids`/`mask`/`segment_ids` in, float embedding out) + `vocab.txt`
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: MediaPipe `bert_embedder` (`MobileBertEmbedder`, Apache-2.0, ~26 MB) — https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite → bundle as `embedding.tflite`; extract the `vocab.txt` associated file from the model metadata → bundle as `vocab.txt` (same files as the Android leaf). Binaries are never committed.
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLite.xcworkspace` (always the workspace, never the bare `.xcodeproj`). Tests: `xcodebuild -workspace MyLeoLite.xcworkspace -scheme MyLeoLite -destination 'platform=iOS,id=<DEVICE_UDID>' test` (+ signing overrides for devices).
