# Android Vector Search — Dual Runtime

- Runtime: LiteRT `com.google.ai.edge.litert:litert:2.2.0` + ONNX Runtime `com.microsoft.onnxruntime:onnxruntime-android:1.30.0`, selectable at runtime, CPU (delegates/EPs later)
- Model format: `models/litert/embedding.tflite` (MiniLM, INT32 inputs located by tensor name, float `[1 x 128 x 384]` sequence out, mean-pooled + L2-normalized in code) + `models/onnx/embedding.onnx` (MiniLM, INT64 in, `last_hidden_state` out, mean-pooled + L2-normalized) — same `all-MiniLM-L6-v2` weights, different extensions — plus per-runtime `vocab.txt`
- Scope contract:
  - Runtime picker first — no model loads until you choose (lazy init, cached per session)
  - Embed text → normalized float vector via the active runtime
  - Shared ObjectBox HNSW index (384-d — both runtimes embed the same weights) — switching runtimes clears it (confirm dialog)
  - Report embed ms + search ms; Compose demo: ingest (free text or Enron emails), query, ranked hits + email detail with query highlighting
- Models (gitignored, never committed, ~86 MB each): `all-MiniLM-L6-v2` (Apache-2.0, 384-d) from the one official checkpoint — `https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2`:
  - `models/onnx/embedding.onnx` — official `onnx/model.onnx` (90,405,214 B, sha256 `6fd5d72f…`) + `models/onnx/vocab.txt` (bert-base-uncased vocab from the same repo).
  - `models/litert/embedding.tflite` — converted from the same repo's `tf_model.h5` with `tools/convert_minilm_tflite.py` (89,744,144 B, sha256 `7aae8dc6…`; INT32 `[1 x 128]` × 3 → FLOAT `[1 x 128 x 384]`, fp32, built-ins only) + `models/litert/vocab.txt` (identical file; sha256 `07eced37…`).
  - Both files fed identical token ids on the host agree to cosine `1.000000` / max |diff| `1.9e-7` per pooled vector — verified before measuring; see `docs/benchmarks.md`.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — copy the sample locally (from the repo root): `cp ios/vector-search/litert/Sources/Resources/enron_sample.csv android/vector-search/dual/app/src/main/assets/enron_sample.csv`. Ingest screen → "Enron emails" source → choose item count.
- Build/run: `./gradlew installDebug` from this module. Tests: `./gradlew :app:testDebugUnitTest`
