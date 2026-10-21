# Model Discovery — Where Models Come From, and How Many Actually Load

Where to look for each LLM runtime, how much is out there, and how much of it works.
Companion to `docs/llm-model-coverage.md` (which checkpoint runs on which runtime)
and `docs/benchmarks.md` (measured speed). All counts are HuggingFace API queries
from **2026-10-16**.

## Summary

| Runtime | Where the models live | Volume | Tagged for the runtime | Share that actually loads |
|---|---|---|---|---|
| LiteRT-LM | `litert-community` (Google's org) | 401 models | 487 under `litert-lm` | high — 24 of the top 25 by downloads ship a `.litertlm` |
| ONNX Runtime GenAI | vendor orgs: `microsoft`, `Arm`, `amd` | 1000+ ONNX repos | only 214 under `onnxruntime-genai` | the tag is trustworthy (24/25 have `genai_config.json`) but the big community org is a trap: 18 of the top 20 `onnx-community` LLM exports have no `genai_config.json` |
| Core ML | `Reza2kn`, plus `apple`/`argmaxinc` for non-LLMs | 1000+ under `coreml`, mostly not LLMs | 73 match `instruct`, 16 `chat`, 3 `llm` | low — LLM exports are rare and come from essentially one account |

Easy to find, in order: **LiteRT-LM → ONNX GenAI → Core ML**. Each is roughly an
order of magnitude harder than the previous one.

## LiteRT-LM — one file, official org

- Format: a single `.litertlm` bundle (weights + tokenizer + chat template).
- Source: the `litert-community` org, named `<Model>-litert-lm`. Volume is real and
  downloads are concentrated on the good ones (`gemma-4-E2B-it-litert-lm` 1.47M,
  `Qwen2.5-1.5B-Instruct` 90k, `Qwen3-0.6B` 46k, `Phi-4-mini-instruct` 16k).
- Repos often carry several variants of the same model — `minicpm_wi4b32_wi8_afp32`
  (W4) vs `MiniCPM5-1B_dynamic_wi8_afp32` (W8) vs `..._gpu_opt` — so pick by size
  and use the `_gpu_opt` build when a GPU backend is in play.
- Two ways to get it wrong anyway:
  - **`.task`-only repos.** Much of the LiteRT ecosystem on HuggingFace is the older
    MediaPipe format. `litert-community/Qwen2.5-0.5B-Instruct`, for example, ships
    `.task` + `.tflite` and no `.litertlm`; the LiteRT-LM `Engine` API cannot load
    those.
  - **Runtime version coupling.** The bundle is versioned with the runtime: the
    MiniCPM5-1B bundles need litert-lm ≥ 0.18.0, and this repo pins 0.17.1 in
    `ios/llm/litert-lm/project.yml` and `android/llm/litert-lm/gradle/libs.versions.toml`.
    "The model exists" is not yet "our pinned runtime loads it".

## ONNX Runtime GenAI — the volume is a trap

- Format: a **directory**, not a file — `model.onnx` + `model.onnx.data` +
  `genai_config.json` + tokenizer files, usually nested (`cpu_and_mobile/...`,
  `gpu/...`).
- The obvious first stop is wrong. `onnx-community` is HuggingFace's own ONNX org
  (1000+ repos, high download counts), but its exports target **transformers.js /
  ORT Web**: 8 `.onnx` chunk files and **no `genai_config.json`**. Measured on the
  20 most-downloaded `onnx-community` `Instruct` repos: 18 lack the config; only
  `onnx-community/Llama-3.2-1B-Instruct-q4f16` and
  `onnx-community/Phi-3.5-mini-instruct-onnx-web` have it.
- The reliable sources are vendor orgs, none of which use the `onnxruntime-genai`
  tag consistently:
  - `microsoft/Phi-4-mini-instruct-onnx` (MIT) — `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4`
    and `gpu/gpu-int4-rtn-block-32`.
  - `Arm/*-onnx-genai-*` — a whole family of int4 builds: llama-3.x 1B/3B/8B,
    qwen2.5-1.5b, qwen3-0.6b, gemma-3-1b, tinyllama.
  - `amd/*`, `LiquidAI/*`, `Emerald7664/*`.
- The `library:onnxruntime-genai` tag is small (214) but trustworthy — 24 of its 25
  most-downloaded have `genai_config.json`. Do **not** rank by downloads here: the
  top tagged model has 891 downloads against 1.47M for the top LiteRT-LM model, so
  popularity signals nothing.
- Three separate gates, in escalating cost:
  1. `genai_config.json` must exist (rules out most of `onnx-community`).
  2. The tokenizer's regex must be inside ORTX's recognized menu —
     `onnx-community/MiniCPM5-1B` **has** the config and still fails to load, with
     `Invalid regex: \p{N}+`. Config-complete does not mean loadable.
  3. The graph must use the fused attention ops the runtime expects
     (`GroupQueryAttention` / `MultiHeadAttention`). `Prince-1/SmolLM2-135M-Instruct`
     keeps dynamic `repeat_kv` reshapes, so generation dies on buffer shapes even
     though the model loads.

## Core ML — fewest, most concentrated

- Format: `.mlpackage` (weights as `Data/com.apple.CoreML/weights/*.bin`) or
  `.mlmodelc`, plus a tokenizer bundle for LLMs.
- The `coreml` tag has 1000+ entries, but the top downloads are embeddings (BERT),
  WhisperKit, depth/vision and Parakeet ASR — not LLMs. LLM-shaped hits are scarce:
  73 for `instruct`, 16 for `chat`, 3 for `llm`.
- Output is concentrated in one account. `Reza2kn` publishes MiniCPM5-1B in Core ML,
  MLX, ONNX-Web, AWQ and NVFP4 flavours; `apple/*` and `argmaxinc/*` are mostly
  non-LLM. Useful (one upstream checkpoint appears in many formats) but a
  single-maintainer dependency for the whole runtime.
- Two look-alikes that are not drop-ins:
  - **MLX** (`mlx-community`, 1000+ models) runs on Apple silicon through MLX, not
    through the Core ML runtime on iOS.
  - **Sharded ANE research exports** such as `mweinbach1/phi4-ctx-2048-coreml` (6
    weight shards + duplicate `.mlmodelc` copies, 2048-token context). The
    `ios/llm/coreml` engine is built around a single `.mlpackage` with a fixed
    128-token context, so it cannot take those.

## Download size and the GPU gate

The Android GPU path in `android/llm/litert-lm` refuses to attempt GPU unless
total RAM ≥ `2 × model + 1.5 GiB` (`GpuPreflight`); the GPU backend keeps a
non-reclaimable second copy of the weights, so a model that fits on CPU can still
be too large to attempt on GPU. Sizes below are the real files; the gate column is
`2 × size + 1.5 GiB` compared against total RAM.

| Model | Copy | Size | GPU gate needs | Galaxy Z Fold7 (10.85 GiB) | 8 GB device (≈7.3 GiB) |
|---|---|---|---|---|---|
| Phi-4-mini q8 | `.litertlm` | 3.9 GB | 8.8 GiB | pass | **skip GPU** |
| Phi-4-mini int4 | ONNX `cpu_and_mobile` | 4.9 GB | 10.6 GiB | pass (barely) | **skip GPU** |
| Phi-4-mini int4 | ONNX `gpu` | 3.4 GB | 7.9 GiB | pass | **skip GPU** |
| MiniCPM5-1B W4 | `.litertlm` | 793 MB | 3.0 GiB | pass | pass |
| MiniCPM5-1B W8 dynamic | `.litertlm` | 1.1 GB | 3.6 GiB | pass | pass |
| MiniCPM5-1B int4 | ONNX | 1.43 GB | 2.4 GiB | pass | pass |
| MiniCPM5-1B fp16 | ONNX | 2.2 GB | 5.6 GiB | pass | pass |

The ONNX rows are illustrative: only the LiteRT-LM leaf implements the gate, and the
ONNX leaves run CPU-only today. They show what a future GPU path would face. The int4
row is the `anydaytv/minicpm5-1b-onnx-int4` export both platforms actually ship; the
fp16 row is the `onnx-community` package, which no released ORT can decode (its
pre-tokenizer uses `\p{N}+` and needs the same 4-byte tokenizer patch).

## Triage a candidate in minutes, before downloading gigabytes

1. **Right format present?** `.litertlm` for LiteRT-LM; `genai_config.json` for
   GenAI; a single `.mlpackage` + tokenizer for Core ML. Rules out most candidates.
2. **Runtime version.** Read the model card for a minimum runtime; check it against
   the pin in the leaf's `project.yml` / `gradle/libs.versions.toml`.
3. **Size vs the gate** (table above), for anything that may run on GPU.
4. **ONNX only — load the tokenizer without the weights.** Fetch just the small
   files (`genai_config.json`, `tokenizer.json`, `tokenizer_config.json`,
   `chat_template.jinja`, a few hundred KB) into a directory and ask the runtime to
   build the tokenizer. This catches both the ORTX regex limit and a missing
   config, and it takes seconds instead of a multi-gigabyte download:

   ```bash
   python3 -c "
   import onnxruntime_genai as og
   t = og.Tokenizer('dir')
   print('loads;', list(t.encode('1/2 and/or 100')))"
   ```

   A tokenizer that loads here is not proof the weights generate (step 3 of the ONNX
   gates above still applies), but a failure here is proof the candidate is out, and
   no multi-gigabyte download was needed to find that out.
5. **Only then** download the weights.

## Noted changes

- **2026-10-16 — MiniCPM5-1B became a single-model option for all three runtimes.**
  The ONNX gap was not a missing export: `onnx-community/MiniCPM5-1B` is rejected by
  ORTX's pre-tokenizer regex menu, and a 4-byte edit to `tokenizer.json`
  (`\p{N}+` → `\p{N}{1,3}`) makes it load, token-id identical to the HuggingFace
  tokenizer. Full recipe, root cause and verification in
  `docs/llm-model-coverage.md`.
- **Discovery lesson from that change:** rank ONNX candidates by looking at the
  files, not the download counts or the `onnx` tag. `onnx-community/MiniCPM5-1B` has
  a perfect `genai_config.json` and 2.2 GB of weights and still would not load.
