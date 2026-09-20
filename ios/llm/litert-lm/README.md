# iOS LLM — LiteRT-LM

- Runtime: LiteRT-LM (`.litertlm` bundles, GPU delegate)
- Model format: `.litertlm` chat model (e.g. Gemma) + tokenizer + config
- Scope contract:
  - Load model from bundle / download path
  - Stream tokens for a prompt (temperature, top-k, top-p, max-tokens)
  - Report TTFT + tokens/sec
  - Minimal SwiftUI chat: system prompt + thread + streaming
- Model: TBD — link name + URL + license + size here; placement: app bundle `Models/`
- Build/run: `xcodegen generate`, then `open MyLeoLLMLiteRTLM.xcodeproj` or `xcodebuild -scheme MyLeoLLMLiteRTLM -destination 'platform=iOS Simulator,name=iPhone 17' build`
