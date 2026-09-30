# Android Vector Search — ONNX

- Runtime: `com.microsoft.onnxruntime:onnxruntime-android:1.30.0`, raw `OrtSession` API on CPU (NNAPI / GPU EP later)
- Model format: `.onnx` MiniLM sentence model (INT64 `input_ids`/`attention_mask`/`token_type_ids` `[1 x 128]` in, float `last_hidden_state` `[1 x 128 x 384]` out, mean-pooled + L2-normalized in code) + WordPiece `vocab.txt`
- Scope contract:
  - Tokenize + embed text → normalized float vector
  - On-device index (ObjectBox HNSW, COSINE) with top-k search + scores
  - Report embed ms + search ms
  - Compose demo: ingest (free text or Enron emails), query, ranked hits + email detail with query highlighting
- Model: `all-MiniLM-L6-v2` (Apache-2.0, ~90 MB) — same weights as the iOS onnx leaf — https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx → place as `app/src/main/assets/models/embedding.onnx`; vocab from https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt → `app/src/main/assets/models/vocab.txt`. 384-d output. `app/src/main/assets/` is gitignored — never commit binaries.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — the default vector-search dataset, see Datasets in the root `README.md` for provenance. Copy the sample locally (from the repo root): `cp ios/vector-search/litert/Sources/Resources/enron_sample.csv android/vector-search/onnx/app/src/main/assets/enron_sample.csv`. Ingest screen → "Enron emails" source → choose item count. Each email becomes its own `Email N` document fed into the text chunker.
- Build/run: `./gradlew installDebug` from this module. Tests: `./gradlew :app:testDebugUnitTest`
