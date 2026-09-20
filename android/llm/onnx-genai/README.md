# Android LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI AAR (`onnxruntime-genai`), Kotlin API
- Model format: `.onnx` generative model + tokenizer (e.g. Phi, SmolLM, Llama) + `genai_config.json`
- Scope contract:
  - Load model from assets / download path
  - Stream tokens for a prompt (temperature, top-k, top-p, max-tokens)
  - Report TTFT + tokens/sec
  - Minimal Compose chat: system prompt + thread + streaming
- Model: TBD — link name + URL + license + size here; placement: `assets/models/`
- Future build/run: `./gradlew installDebug` from this module (not scaffolded yet)
