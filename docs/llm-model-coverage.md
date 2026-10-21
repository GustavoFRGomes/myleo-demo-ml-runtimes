# LLM Model Coverage — Which Checkpoint Runs on Which Runtime

Question: is there a single model checkpoint that runs on all three LLM runtimes
(LiteRT-LM, ONNX Runtime GenAI, Core ML)?

Short answer: **yes — MiniCPM5-1B** (as of 2026-10-16). It needs one documented
4-byte edit to the ONNX copy's `tokenizer.json` and LiteRT-LM ≥ 0.18.0. The other
model, Phi-4-mini-instruct, still cannot reach Core ML.

Companion docs: `docs/model_discovery.md` (where models come from per runtime, how
many load, how to triage a candidate), `docs/minicpm5-1b.md` (download URLs and file
lists per runtime) and `docs/benchmarks.md` (measured speed).

## Coverage matrix

| Model | LiteRT-LM (`.litertlm`) | ONNX Runtime GenAI | Core ML (`.mlpackage`) |
|---|---|---|---|
| Phi-4-mini-instruct (3.8B, MIT) | ✓ `litert-community/Phi-4-mini-instruct` (q8, ~3.9 GB) | ✓ `microsoft/Phi-4-mini-instruct-onnx` (int4, ~4.9 GB) | ✗ sharded LUT export only |
| **MiniCPM5-1B (1B, Apache-2.0)** | ✓ `litert-community/MiniCPM5-1B` (W4, ~793 MB, needs runtime ≥ 0.18.0) | ✓ `onnx-community/MiniCPM5-1B` with the 4-byte `tokenizer.json` fix below | ✓ `Reza2kn/MiniCPM5-1B-CoreML-4bit` (int4, ~619 MB) |
| Qwen2.5-1.5B (Apache-2.0) | ✓ (`.litertlm` exists) | ~ exports exist, tokenizer unverified | ✗ nothing downloadable |
| SmolLM2-135M/360M (Apache-2.0) | ✓ (`.litertlm` exists) | ✗ no compatible export | ✗ nothing |
| TinyLlama-1.1B-Chat | ✗ (`.task` only, no `.litertlm`) | ~ device-specific builds only | ✗ community fp16, too big |

## MiniCPM5-1B on ONNX Runtime GenAI — the 4-byte tokenizer fix

`onnx-community/MiniCPM5-1B` is rejected at load time with
`Invalid regex: \p{N}+`. Root cause: ORT GenAI delegates tokenization to
`onnxruntime-extensions` (ORTX), whose BPE pre-tokenizer compiles each `Split`
regex against a **fixed menu of known alternation branches**
(`operators/tokenizer/bpe_utils.hpp`). Branches that are in the menu become
hand-written matchers; whatever is left over must compile with `std::regex`,
which has no `\p{...}` class. MiniCPM5's main `Split` uses `\p{N}+`, which is not
in the menu (`\p{N}{1,3}` and `\p{N}` are), so the leftover `\p{N}+` reaches
`std::regex` and fails.

The edit — one atom, in `tokenizer.json`, applied before the runtime reads the file:

- Find the only occurrence of the JSON text `\\p{N}+` and replace it with
  `\\p{N}{1,3}` (4 bytes longer). The doubled backslashes are the JSON escape for
  a single regex backslash, so this rewrites the regex atom `\p{N}+` that sits in
  the second `Split`'s pattern.

Keep both `Split` steps. The replacement is behaviour-preserving: the first
`Split` (`\p{N}{1,3}`, `behavior: Isolated`) already chunks every digit run into
groups of at most 3, so `\p{N}{1,3}` and `\p{N}+` match the same text in the
second pattern.

Do **not** substitute the canonical single-`Split` LLAMA-3 pattern. It also loads,
but silently changes tokenization (16 of 4,000 fuzz cases, all involving tabs) —
it is not the checkpoint's tokenizer any more.

Verified against the runtime the leaves ship (GenAI `0.17.0` → ORTX `0353c93d96`),
loading the model directory through the GenAI `Tokenizer` API:

- unpatched → `Invalid regex: \p{N}+` (reproduces the reported failure);
- patched → loads, and token ids are **identical to the HuggingFace tokenizer** for
  all 31 realistic strings and 20,000 fuzz strings tested (0 mismatches).

The same classification decides any other checkpoint: every alternation branch of
every `Split` must appear in ORTX's menu, or the model needs an equivalent rewrite.

## Why each gap exists

- **No Phi-4-mini Core ML export.** `AlonBBar/phi4mini-float16` is 7.67 GB fp16
  (won't fit a 4 GB iPhone); the 2.16 GB entry is a different fine-tune with an
  unknown interface; `mweinbach1/phi-4-mini-512ctx-coreml` is an empty repo.
  `mweinbach1/phi4-ctx-2048-coreml` does exist, but it is a sharded Apple-style LUT
  export (6 weight shards plus duplicate `.mlmodelc` copies, 2048-token context,
  no README, no license), so it cannot replace the single-`mlpackage`, 128-token
  engine the `coreml` leaf is built around. MLX conversions (e.g. `mlx-community`)
  run on Mac via MLX, not on iOS via Core ML. The `ane-book` converters build
  sharded ANE research pieces, not a downloadable model.
- **SmolLM2's only ORT GenAI export** (`Prince-1/SmolLM2-135M-Instruct`) keeps
  dynamic `repeat_kv` reshapes instead of fused `GroupQueryAttention`, so
  generation dies with buffer-shape errors.
- **TinyLlama has no `.litertlm`.** Only MediaPipe `.task` bundles exist, which the
  LiteRT-LM `Engine` API cannot load.

## What we use where

- Every LLM leaf except `ios/llm/coreml` now runs **MiniCPM5-1B**: `litert-lm` on
  both platforms, `onnx-genai` on both platforms, and all three runtimes of
  `ios/llm/trio` (LiteRT-LM, ONNX GenAI, Core ML). `android/llm/dual` is the last
  leaf still on Phi-4-mini behind its picker.
- `ios/llm/coreml` and the trio's Core ML runtime: **MiniCPM5-1B** (default) +
  Apple Foundation Models (system option). Documented in that leaf's README.

## Remaining work

Nothing is missing from the one-model story any more. MiniCPM5-1B is downloadable
in all three formats, so unifying on it is editing plus a runtime bump
(`litert-lm` 0.17.1 → ≥ 0.18.0 in `ios/llm/litert-lm/project.yml` and
`android/llm/litert-lm/gradle/libs.versions.toml`), not a conversion job. The
trade-off is model size: 1B instead of 3.8B parameters (LiteRT-LM's own W4 vs FP
figures: 60.91 vs 66.16 overall), and the Core ML engine's 128-token context
stays as it is.

Sizes differ a lot per runtime, which matters on Android where the GPU path needs
total RAM ≥ `2 × model + 1.5 GiB` (`GpuPreflight`): 793 MB (W4 `.litertlm`), 1.43 GB
(int4 ONNX, `anydaytv/minicpm5-1b-onnx-int4`) and 619 MB (Core ML). Against Phi-4-mini's
3.9 GB / 3.4 GB that is the difference between "8 GB phones can attempt GPU" and
"they cannot". Full table in `docs/model_discovery.md`.

Phi-4-mini-instruct on Core ML remains the only genuine gap: a 3.8B,
fixed-context `.mlpackage` plus a matching tokenizer bundle means a multi-hour
`coremltools` run on a large-RAM Mac and hosting for a ~2 GB result.
