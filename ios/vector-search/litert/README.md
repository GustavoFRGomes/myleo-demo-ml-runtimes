# iOS Vector Search — LiteRT

- Runtime: `TensorFlowLiteSwift` `~> 2.17.0` via CocoaPods (raw `Interpreter` API, CPU; GPU/Core ML delegates later)
- Model format: `.tflite` BERT WordPiece embedding (INT32 `ids`/`mask`/`segment_ids` in, float embedding out) + `vocab.txt`
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: MediaPipe `bert_embedder` (`MobileBertEmbedder`, Apache-2.0, ~26 MB) — https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite → bundle as `embedding.tflite`; extract the `vocab.txt` associated file from the model metadata → bundle as `vocab.txt` (same files as the Android leaf). Binaries are never committed.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — the default vector-search dataset, see Datasets in the root `README.md` for provenance. The app bundles a 300-row sample — regenerate it with:
  `python3 -c "import csv; r=csv.DictReader(open('enron_data_fraud_labeled.csv',newline='',encoding='utf-8')); w=csv.DictWriter(open('ios/vector-search/litert/Sources/Resources/enron_sample.csv','w',newline='',encoding='utf-8'),fieldnames=r.fieldnames); w.writeheader(); [w.writerow(row) for _,row in zip(range(300),r)]"`
  Ingest screen → "Enron emails" source → choose item count. Each email becomes one raw string (`Folder-Name Date From To Subject Body Cc Bcc`, whitespace-collapsed) fed into the text chunker.
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLite.xcworkspace` (always the workspace, never the bare `.xcodeproj`). Tests: `xcodebuild -workspace MyLeoLite.xcworkspace -scheme MyLeoLite -destination 'platform=iOS,id=<DEVICE_UDID>' test` (+ signing overrides for devices).
