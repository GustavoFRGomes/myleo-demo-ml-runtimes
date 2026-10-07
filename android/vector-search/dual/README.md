# Android Vector Search — Dual Runtime

- Runtime: LiteRT `com.google.ai.edge.litert:litert:2.2.0` + ONNX Runtime `com.microsoft.onnxruntime:onnxruntime-android:1.30.0`, selectable at runtime, CPU (delegates/EPs later)
- Model format: `models/litert/embedding.tflite` (MiniLM, INT32 inputs located by tensor name, float `[1 x 128 x 384]` sequence out, mean-pooled + L2-normalized in code) + `models/onnx/embedding.onnx` (MiniLM, INT64 in, `last_hidden_state` out, mean-pooled + L2-normalized) — same `all-MiniLM-L6-v2` weights, different extensions — plus per-runtime `vocab.txt`
- Scope contract:
  - Runtime picker first — no model loads until you choose (lazy init, cached per session)
  - Embed text → normalized float vector via the active runtime
  - Shared ObjectBox HNSW index (384-d — both runtimes embed the same weights) — switching runtimes clears it (confirm dialog)
  - Report embed ms + search ms; Compose demo: ingest (free text or Enron emails), query, ranked hits + email detail with query highlighting
- Models (gitignored, never committed): `all-MiniLM-L6-v2` (Apache-2.0, ~90 MB per file, 384-d) — TFLite from the iOS litert leaf (`ios/vector-search/litert/Sources/Resources/embedding.tflite`) → `app/src/main/assets/models/litert/`; ONNX from the onnx leaf (`Xenova/all-MiniLM-L6-v2`, see its README) → `app/src/main/assets/models/onnx/`; `vocab.txt` (bert-base-uncased) alongside each. Copy locally from the sibling leafs' assets.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — copy the sample locally (from the repo root): `cp ios/vector-search/litert/Sources/Resources/enron_sample.csv android/vector-search/dual/app/src/main/assets/enron_sample.csv`. Ingest screen → "Enron emails" source → choose item count.
- Build/run: `./gradlew installDebug` from this module. Tests: `./gradlew :app:testDebugUnitTest`
