# iOS LLM — Core ML

- Runtime: Apple Foundation Models (system-hosted, where available) with Core ML `.mlpackage` fallback
- Model format: system model via Foundation Models API; fallback `.mlpackage`
- Scope contract:
  - Check availability, load / open session
  - Stream tokens for a prompt (temperature, max-tokens where supported)
  - Report TTFT + tokens/sec
  - Minimal SwiftUI chat: system prompt + thread + streaming
- Model: system-hosted (no download); fallback TBD — link name + URL + license + size here
- Build/run: `xcodegen generate`, then `open MyLeoLLMCoreML.xcodeproj` or `xcodebuild -scheme MyLeoLLMCoreML -destination 'platform=iOS Simulator,name=iPhone 17' build`
