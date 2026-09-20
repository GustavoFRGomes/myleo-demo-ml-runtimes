# Android Vector Search — LiteRT

- Runtime: LiteRT (`com.google.ai.edge.litert`), GPU / NPU delegates, Kotlin API
- Model format: `.tflite` sentence embedding (e.g. Universal Sentence Encoder, MiniLM)
- Scope contract:
  - Embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal Compose demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: `assets/models/`
- Future build/run: `./gradlew installDebug` from this module (not scaffolded yet)
