# LLM Model Coverage — Which Checkpoint Runs on Which Runtime

Question: is there a single model checkpoint that runs on all three LLM runtimes
(LiteRT-LM, ONNX Runtime GenAI, Core ML)?

Short answer: **no** (as of 2026-10-01). The pairs cover two runtimes each;
nothing covers all three in downloadable, working form.

## Coverage matrix

| Model | LiteRT-LM (`.litertlm`) | ONNX Runtime GenAI | Core ML (`.mlpackage`) |
|---|---|---|---|
| Phi-4-mini-instruct (3.8B, MIT) | ✓ `litert-community/Phi-4-mini-instruct` (q8, ~3.9 GB) | ✓ `microsoft/Phi-4-mini-instruct-onnx` (int4, ~4.9 GB) | ✗ no usable export |
| MiniCPM5-1B (1B, Apache-2.0) | ✓ `litert-community/MiniCPM5-1B` (W4, ~793 MB) | ✗ tokenizer rejected | ✓ `Reza2kn/MiniCPM5-1B-CoreML-4bit` (int4, ~619 MB) |
| Qwen2.5-1.5B (Apache-2.0) | ✓ (`.litertlm` exists) | ~ exports exist, tokenizer unverified | ✗ nothing downloadable |
| SmolLM2-135M/360M (Apache-2.0) | ✓ (`.litertlm` exists) | ✗ no compatible export | ✗ nothing |
| TinyLlama-1.1B-Chat | ✗ (`.task` only, no `.litertlm`) | ~ device-specific builds only | ✗ community fp16, too big |

## Why each gap exists

- **ONNX GenAI rejects MiniCPM/Qwen-style tokenizers.** ORT GenAI's built-in
  tokenizer errors on the regex pre-tokenizer (`Invalid regex: \p{N}+`). Verified
  for both `anydaytv/minicpm5-1b-onnx-int4` and `onnx-community/MiniCPM5-1B`
  tokenizers; Qwen2.5 uses the same regex family. SmolLM2's only ORT GenAI export
  (`Prince-1/SmolLM2-135M-Instruct`) keeps dynamic `repeat_kv` reshapes instead of
  fused `GroupQueryAttention`, so generation dies with buffer-shape errors.
- **No Phi-4-mini Core ML export exists.** `AlonBBar/phi4mini-float16` is 7.67 GB
  fp16 (won't fit a 4 GB iPhone); the 2.16 GB entry is a different fine-tune with
  an unknown interface; `mweinbach1/phi-4-mini-512ctx-coreml` is an empty repo.
  MLX conversions (e.g. `mlx-community`) run on Mac via MLX, not on iOS via Core ML.
  The `ane-book` converters build sharded ANE research pieces, not a downloadable model.
- **TinyLlama has no `.litertlm`.** Only MediaPipe `.task` bundles exist, which the
  LiteRT-LM `Engine` API cannot load.

## What we use where

- Every `litert-lm` / `onnx-genai` leaf (both platforms), plus `android/llm/dual`
  and the LiteRT-LM + ONNX GenAI runtimes of `ios/llm/trio`: **Phi-4-mini-instruct**.
- `ios/llm/coreml` and the trio's Core ML runtime: **MiniCPM5-1B** (default) +
  Apple Foundation Models (system option). Documented in that leaf's README,
  including everything a Phi-4-mini port would have to change.

## What would close the gap

- A 4-bit Phi-4-mini-instruct Core ML export with a fixed context and matching
  tokenizer bundle (custom `coremltools` conversion; hours on a 16 GB Mac, then
  hosting for the ~2 GB result), or
- A MiniCPM5-1B re-export with an ORT-GenAI-compatible tokenizer (not the same
  files as iOS, so parity would be approximate).

Neither is a download; both are conversion jobs. Until one exists, the two-pair
split above is the best achievable.
