# MiniCPM5-1B — Download Map for Every Runtime

MiniCPM5-1B is the one checkpoint that runs on all three LLM runtimes, and it is what every
`llm/` leaf uses in place of Phi-4-mini-instruct. Apache-2.0, 1.08 B parameters, 24 layers,
hidden 1536, 16 query / 2 KV heads, head dim 128, vocab 130560, 131072-token context.

Companion docs: `docs/llm-model-coverage.md` (why this model, plus the ONNX tokenizer fix),
`docs/model_discovery.md` (how to triage a new candidate), `docs/benchmarks.md` (measured speed).

## What each leaf downloads

| Leaf | Runtime | Hugging Face repo | Package | Size |
|---|---|---|---|---|
| `ios/llm/litert-lm`, `ios/llm/trio` | LiteRT-LM ≥ 0.18.0 | `litert-community/MiniCPM5-1B` | one `.litertlm` | 793,029,040 B (~756 MiB) |
| `android/llm/litert-lm`, `android/llm/dual` | LiteRT-LM ≥ 0.18.0 | `litert-community/MiniCPM5-1B` | one `.litertlm` | 793,029,040 B (~756 MiB) |
| `ios/llm/onnx-genai`, `android/llm/onnx-genai`, `android/llm/dual` | ONNX Runtime GenAI 0.17.0 | `anydaytv/minicpm5-1b-onnx-int4` | 4 files, int4 (+ config written by the leaf) | 1,429,810,597 B (~1.33 GiB) |
| `ios/llm/coreml` | Core ML | `Reza2kn/MiniCPM5-1B-CoreML-4bit` | `.mlpackage` + tokenizer | 618,783,248 B (~590 MiB) |

## LiteRT-LM — one file, GPU-optimised

Repo: https://huggingface.co/litert-community/MiniCPM5-1B

| File | Size | Use |
|---|---|---|
| `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` | 793,029,040 B | **shipped default** — mixed W4/W8, GPU-optimised |
| `minicpm_wi4b32_wi8_afp32.litertlm` | 792,619,648 B | same weights, no GPU tuning |
| `MiniCPM5-1B_dynamic_wi8_afp32.litertlm` | 1,105,464,896 B | W8 dynamic — larger, slower, more accurate |

```text
https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm
```

sha256 `40ffba999ca45223c1713f130299e69b798b72fd561ea420300beb0adb49e191`.

Placement: `Documents/Models/` (iOS) or `filesDir/models/` (Android). Both W4 bundles need
LiteRT-LM ≥ 0.18.0 — older runtimes reject them.

## ONNX Runtime GenAI — four files, int4 builder export

Repo: https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4

This is an `onnxruntime-genai` **model builder** export of `openbmb/MiniCPM5-1B`
(`-p int4 -e cpu --extra_options accuracy_level=4 block_size=32`), not a generic
transformers export. All four files sit at the repository root.

| File | Size | sha256 |
|---|---|---|
| `model.onnx` | 181,078 B | `9fd8adba2cfd05e082d941d11bc9bcee75319853b93e420d98fc30d4fe87a495` |
| `model.onnx.data` | 1,419,640,832 B | `63000fd8a316adf807fe875767e8e4a8a9200279cb0ef290d8005a56cbcbf985` |
| `tokenizer.json` | 9,894,271 B | `3e065a558a034185fe299917b398685c1facd0169a9eea1e629eb30c171fed81` |
| `tokenizer_config.json` | 94,416 B | `094efb3cf1ff412284cc5945fc99dff58673a912760d04483a04aa1c716f66fd` |

```text
https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4/resolve/main/<file>
```

Total 1,429,810,597 B (~1.33 GiB). **`genai_config.json` is not published** — the leaves
write the builder template themselves (`MiniCPM5GenAIConfig`): GQA-fused decoder, no
`position_ids` input (RoPE is fused), and `past_present_share_buffer: true`.

Why not the fp16 package (`onnx-community/MiniCPM5-1B`, 2.2 GB, six files)? It **cannot
decode on any released ONNX Runtime**. Its fp16 `repeat_kv` forces ONNX Runtime to wrap
all 24 attention ops in precision casts, and the allocation planner then sizes those
buffers from the prefill length, so the first generated token throws
`Shape mismatch attempting to re-use buffer`. The upstream fix is
[onnxruntime#32717](https://github.com/microsoft/onnxruntime/pull/32717) (merged
2026-10-15), which landed *after* ORT `1.31.0` shipped and is in no release yet. The
builder export has none of that structure (24 fused `GroupQueryAttention`, fp32
activations, static KV cache).

`tokenizer.json` still needs the same patch as before: replace the single `\p{N}+` with
`\p{N}{1,3}` (+4 bytes). The leaves do this automatically (`TokenizerPatch`); the failure
mode and verification live in `docs/llm-model-coverage.md`.

Placement: `filesDir/models/minicpm5-1b-onnx-int4/` (Android);
`Documents/Models/minicpm5-1b-onnx-int4/` (iOS, not yet switched over).

## Core ML — 4-bit, fixed 128-token context

Repo: https://huggingface.co/Reza2kn/MiniCPM5-1B-CoreML-4bit

| File | Size |
|---|---|
| `MiniCPM5-1B-CoreML-4bit.mlpackage/Data/com.apple.CoreML/weights/weight.bin` | 608,400,256 B |
| `MiniCPM5-1B-CoreML-4bit.mlpackage/Data/com.apple.CoreML/model.mlmodel` | 440,798 B |
| `MiniCPM5-1B-CoreML-4bit.mlpackage/Manifest.json` | 617 B |
| `tokenizer.json` | 9,894,271 B |
| `tokenizer_config.json` | 460 B |
| `chat_template.jinja` | 9,062 B |

Stateless: it re-primes the KV cache from a 128-token window on every turn. Placement:
`Documents/Models/MiniCPM5-1B-CoreML-4bit/`. Core ML has no Android leaf.

## Every URL in one block

```bash
huggingface-cli download litert-community/MiniCPM5-1B \
  minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm --local-dir models/

huggingface-cli download anydaytv/minicpm5-1b-onnx-int4 \
  model.onnx model.onnx.data tokenizer.json tokenizer_config.json \
  --local-dir models/onnx/

huggingface-cli download Reza2kn/MiniCPM5-1B-CoreML-4bit \
  --include "MiniCPM5-1B-CoreML-4bit.mlpackage/*" \
  --include "tokenizer*.json" --include "chat_template.jinja" \
  --local-dir models/coreml/
```

## Replaces: Phi-4-mini-instruct

| Runtime | Old repo | Old package |
|---|---|---|
| LiteRT-LM | `litert-community/Phi-4-mini-instruct` | `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (3,910,090,752 B) |
| ONNX GenAI | `microsoft/Phi-4-mini-instruct-onnx` | `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB, 7 files) |
| Core ML | — | no usable export |

No model binary is ever committed: each leaf downloads it into its own app sandbox on first
launch, and the bundle/asset directories are gitignored.

## License

Apache-2.0, from `openbmb/MiniCPM5-1B` (the base model). The three packaging repos inherit
it; `Reza2kn/MiniCPM5-1B-CoreML-4bit` also ships its own `LICENSE` file.
