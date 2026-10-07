# Android Vector Search — LiteRT

- Runtime: `com.google.ai.edge.litert:litert:2.2.0`, raw `org.tensorflow.lite` Interpreter API on CPU (GPU/NPU delegates later). Note: `litert` + `litert-api` AARs share one manifest package — see `android.uniquePackageNames=false` in `gradle.properties`.
- Model format: `.tflite` BERT WordPiece embedding — INT32 `ids`/`mask`/`segment_ids` (`[1 x seqLen]`) in, float embedding (`[1 x N]`) out — plus `vocab.txt` (one token per line).
- Scope contract:
  - Embed text → normalized float vector
  - On-device index (ObjectBox HNSW, COSINE) with top-k search + scores
  - Report embed ms + search ms
  - Compose demo: ingest (free text or Enron emails), query, ranked hits + email detail with query highlighting
- Model: MediaPipe `bert_embedder` (`MobileBertEmbedder`, Apache-2.0, ~26 MB) — https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite → place as `app/src/main/assets/models/embedding.tflite`. Vocab (`vocab.txt`, 30522 WordPiece tokens) ships packed inside the model metadata — extract the `vocab.txt` associated file → `app/src/main/assets/models/vocab.txt`. Verified contract: INT32 `input_word_ids`/`input_mask`/`input_type_ids` `[1 x 128]` in, float32 `[1 x 512]` out; reference pair scores ≈0.99 like MediaPipe's own demo. `app/src/main/assets/` is gitignored — never commit binaries.
- Dataset (gitignored, never committed): `enron_data_fraud_labeled.csv` (~752 MB, repo root) — the default vector-search dataset, see Datasets in the root `README.md` for provenance. Copy the sample locally (from the repo root): `cp ios/vector-search/litert/Sources/Resources/enron_sample.csv android/vector-search/litert/app/src/main/assets/enron_sample.csv`. Ingest screen → "Enron emails" source → choose item count. Each email becomes its own `Email N` document fed into the text chunker.
- Build/run: `./gradlew installDebug` from this module. Tests: `./gradlew :app:testDebugUnitTest`
