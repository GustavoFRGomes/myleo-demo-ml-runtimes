# iOS LLM — LiteRT-LM

- Runtime: LiteRT-LM `LiteRTLM` Swift package `0.17.1` (prebuilt `CLiteRTLM.xcframework`), GPU backend by default, CPU fallback configurable
- Model format: `.litertlm` chat bundle (weights + tokenizer + chat template in one file)
- Scope contract:
  - Load model from a resolved path; **download it in-app from Hugging Face** with progress + cancel when absent
  - Show model id + load state + errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear, plus error and empty states
  - Report TTFT + tokens/sec per generation, shown under each assistant reply
  - Info sheet (FAB): model file, size, Hugging Face source URL, load time, and a log of every generation with its result + timing
  - **Export transcript as Markdown or JSON**: model info, parameters, every prompt + response, per-message metrics, and an aggregate performance summary (tokens, avg TTFT, avg/min/max tok/s)
  - SwiftUI chat: system prompt field, streaming message thread, settings sheet
- Model: **Phi-4-mini-instruct** (MIT) — https://huggingface.co/litert-community/Phi-4-mini-instruct. Preferred: `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (~3.9 GB, q8). Same base model as the other LLM leaves. Binaries are never committed.
- Model delivery: on first launch the app downloads the preferred file from `https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/` into `Documents/Models/` (`URLSessionDownloadTask`, progress + cancel). No manual step needed; you can also drop a `.litertlm` into that folder yourself. `ModelLocator` documents both lookup paths.
- Build/run: `xcodegen generate`, then `open MyLeoLLMLiteRTLM.xcodeproj` (SPM resolves the LiteRT-LM package on first build; needs network once). The LiteRT-LM repo tracks LFS files, so either `brew install git-lfs && git lfs install`, or build with `GIT_LFS_SKIP_SMUDGE=1` (the prebuilt `CLiteRTLM.xcframework` comes from a release zip, not LFS). CLI test: `GIT_LFS_SKIP_SMUDGE=1 xcodebuild -scheme MyLeoLLMLiteRTLM -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- API note: pinned to `0.17.1`, where the model-metadata type is `Capabilities` (`main` renamed it to `ModelInfo`). Bump `exactVersion` in `project.yml` intentionally, not incidentally.
- Device note: a 3.8B q8 model needs ~4 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec (the simulator is CPU-only and unrepresentative).
