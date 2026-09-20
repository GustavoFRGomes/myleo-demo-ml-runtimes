# iOS LLM — LiteRT-LM

- Runtime: LiteRT-LM (`.litertlm` bundles, GPU delegate)
- Model format: `.litertlm` chat model (e.g. Gemma) + tokenizer + config
- Scope contract:
  - Load model from bundle / download path
  - Stream tokens for a prompt (temperature, top-k, top-p, max-tokens)
  - Report TTFT + tokens/sec
  - Minimal SwiftUI chat: system prompt + thread + streaming
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Future build/run: open this folder's Xcode project / SPM package (not scaffolded yet)
