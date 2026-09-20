# iOS LLM — Core ML

- Runtime: Apple Foundation Models (system-hosted, where available) with Core ML `.mlpackage` fallback
- Model format: system model via Foundation Models API; fallback `.mlpackage`
- Scope contract:
  - Check availability, load / open session
  - Stream tokens for a prompt (temperature, max-tokens where supported)
  - Report TTFT + tokens/sec
  - Minimal SwiftUI chat: system prompt + thread + streaming
- Model: system-hosted (no download); fallback TBD — link name + URL + license + size here
- Future build/run: open this folder's Xcode project / SPM package (not scaffolded yet)
