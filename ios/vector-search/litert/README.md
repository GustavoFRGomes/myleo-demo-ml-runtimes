# iOS Vector Search — LiteRT

- Runtime: `TensorFlowLiteSwift` `~> 2.17.0` via CocoaPods (raw `Interpreter` API, CPU; GPU/Core ML delegates later)
- Model format: `.tflite` MiniLM sentence model (Int32 inputs located by tensor *name* — order varies per file — float `[1 x seqLen x dim]` sequence out, mean-pooled + L2-normalized in code) + WordPiece `vocab.txt` (same vocabulary text as BERT)
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: `all-MiniLM-L6-v2` (Apache-2.0, ~90 MB) — same weights as the onnx/coreml leafs — converted `sentence-transformers/all-MiniLM-L6-v2` (https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) via `TFAutoModel` (`transformers` 4.x with TF support) + `TFLiteConverter` (built-ins only, no flex ops) → bundle as `embedding.tflite`; vocab from https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt → `vocab.txt`. 384-d output, numerically identical to Torch (demo pair 0.7609/0.0157). Binaries are never committed.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — the default vector-search dataset, see Datasets in the root `README.md` for provenance. The app bundles a 300-row sample — regenerate it with:
  `python3 -c "import csv; r=csv.DictReader(open('enron_data_fraud_labeled.csv',newline='',encoding='utf-8')); w=csv.DictWriter(open('ios/vector-search/litert/Sources/Resources/enron_sample.csv','w',newline='',encoding='utf-8'),fieldnames=r.fieldnames); w.writeheader(); [w.writerow(row) for _,row in zip(range(300),r)]"`
  Ingest screen → "Enron emails" source → choose item count. Each email becomes one raw string (`Folder-Name Date From To Subject Body Cc Bcc`, whitespace-collapsed) fed into the text chunker.
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLite.xcworkspace` (always the workspace, never the bare `.xcodeproj`). Tests: `xcodebuild -workspace MyLeoLite.xcworkspace -scheme MyLeoLite -destination 'platform=iOS,id=<DEVICE_UDID>' test` (+ signing overrides for devices).
