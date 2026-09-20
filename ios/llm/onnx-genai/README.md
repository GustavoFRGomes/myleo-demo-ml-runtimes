# iOS LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI (via thin Obj-C/C++ bridge; Swift UI layer only)
- Model format: `.onnx` generative model + tokenizer (e.g. Phi, SmolLM, Llama) + `genai_config.json`
- Scope contract:
  - Load model from bundle / download path
  - Stream tokens for a prompt (temperature, top-k, top-p, max-tokens)
  - Report TTFT + tokens/sec
  - Minimal SwiftUI chat: system prompt + thread + streaming
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Future build/run: open this folder's Xcode project / SPM package (not scaffolded yet)
