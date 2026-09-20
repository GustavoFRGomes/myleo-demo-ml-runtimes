# Android Vector Search — LiteRT

- Runtime: `com.google.ai.edge.litert:litert:2.2.0`, raw `org.tensorflow.lite` Interpreter API on CPU (GPU/NPU delegates later). Note: `litert` + `litert-api` AARs share one manifest package — see `android.uniquePackageNames=false` in `gradle.properties`.
- Model format: `.tflite` BERT WordPiece embedding — INT32 `ids`/`mask`/`segment_ids` (`[1 x seqLen]`) in, float embedding (`[1 x N]`) out — plus `vocab.txt` (one token per line).
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal Compose demo: ingest docs, query, ranked hits
- Model: TBD — needs a BERT WordPiece `.tflite` matching the contract above (candidate: MediaPipe `bert_embedder`; confirm tensor order/layout with Model Explorer before bundling). Placement: `app/src/main/assets/models/embedding.tflite` + `app/src/main/assets/models/vocab.txt`. Wired via `LiteRtEmbedderFactory.create(context)`; no model needed for unit tests.
- Future build/run: `./gradlew installDebug` from this module (not scaffolded yet)
