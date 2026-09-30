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
- Model: **MiniCPM5-1B** (Apache-2.0) — https://huggingface.co/litert-community/MiniCPM5-1B. Preferred: `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` (~793 MB, W4 GPU-optimized). Alternatives: `minicpm_wi4b32_wi8_afp32.litertlm` (~793 MB), `MiniCPM5-1B_dynamic_wi8_afp32.litertlm` (~1.11 GB). Binaries are never committed.
- Model delivery: on first launch the app downloads the preferred file from `https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/` into `Documents/Models/` (`URLSessionDownloadTask`, progress + cancel). No manual step needed; you can also drop a `.litertlm` into that folder yourself. `ModelLocator` documents both lookup paths.
- Build/run: `xcodegen generate`, then `open MyLeoLLMLiteRTLM.xcodeproj` (SPM resolves the LiteRT-LM package on first build; needs network once). The LiteRT-LM repo tracks LFS files, so either `brew install git-lfs && git lfs install`, or build with `GIT_LFS_SKIP_SMUDGE=1` (the prebuilt `CLiteRTLM.xcframework` comes from a release zip, not LFS). CLI test: `GIT_LFS_SKIP_SMUDGE=1 xcodebuild -scheme MyLeoLLMLiteRTLM -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- API note: pinned to `0.17.1`, where the model-metadata type is `Capabilities` (`main` renamed it to `ModelInfo`). Bump `exactVersion` in `project.yml` intentionally, not incidentally.
- Device note: a 1B model needs ~1 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec (the simulator is CPU-only and unrepresentative).
