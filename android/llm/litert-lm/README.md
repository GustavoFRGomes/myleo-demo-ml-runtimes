# Android LLM — LiteRT-LM

- Runtime: LiteRT-LM via MediaPipe `LlmInference` API, GPU delegate, Kotlin API
- Model format: `.litertlm` / MediaPipe bundle chat model (e.g. Gemma) + tokenizer
- Scope contract:
  - Load model from assets / download path
  - Stream tokens for a prompt (temperature, top-k, top-p, max-tokens)
  - Report TTFT + tokens/sec
  - Minimal Compose chat: system prompt + thread + streaming
- Model: TBD — link name + URL + license + size here; placement: `assets/models/`
- Future build/run: `./gradlew installDebug` from this module (not scaffolded yet)
