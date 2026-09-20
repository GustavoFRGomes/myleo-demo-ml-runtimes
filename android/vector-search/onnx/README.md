# Android Vector Search — ONNX

- Runtime: ONNX Runtime Android AAR, NNAPI / GPU EP where available, Kotlin API
- Model format: `.onnx` embedding model + tokenizer (e.g. MiniLM, E5-small)
- Scope contract:
  - Tokenize + embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal Compose demo: ingest docs, query, ranked hits
- Model: TBD — link name + URL + license + size here; placement: `assets/models/`
- Future build/run: `./gradlew installDebug` from this module (not scaffolded yet)
