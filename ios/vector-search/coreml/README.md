# iOS Vector Search — Core ML

- Runtime: Core ML (`MLModel`, CPU compute units; ANE/GPU later)
- Model format: `.mlpackage` MiniLM sentence model (Int32 `input_ids`/`attention_mask`/`token_type_ids`/`position_ids` in, float `last_hidden_state` out, mean-pooled + L2-normalized in code) + WordPiece `vocab.txt` (same vocabulary text as BERT)
- Scope contract:
  - Embed text → normalized float vector via Core ML prediction
  - On-device index with top-k cosine search (Accelerate) + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: `all-MiniLM-L6-v2` (Apache-2.0) converted to Core ML with `coremltools` 9.0 (`transformers` 4.36.2, explicit `position_ids` input, `compute_precision=FLOAT32`, iOS 17 target) from https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2 → bundle as `embedding.mlpackage` (~86 MB); vocab from https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt → `vocab.txt`. 384-d output, numerically identical to Torch (cosine 1.0). Binaries are never committed. Note: coremltools' default fp16 conversion degrades this model — fp32 was chosen deliberately.
- Replacing the model (2 items in `Sources/Resources/`):
  1. `embedding.mlpackage/` — a directory (model + weights). Any transformer sentence model works, BUT tensor names are hardcoded in `CoreMLRunner`: Int32 inputs must be named exactly `input_ids`/`attention_mask`/`token_type_ids`/`position_ids`, output exactly `last_hidden_state` (float `[1 x seqLen x dim]`). Convert with `compute_precision=FLOAT32` (fp16 measurably degrades MiniLM) and minimum deployment iOS 17. Xcode compiles it to `embedding.mlmodelc` at build time — that compiled copy is what ships/loads.
  2. `vocab.txt` — must be the training vocab of that exact model (one token per line, `[PAD]`/`[UNK]`/`[CLS]`/`[SEP]` present).
  3. Rebuild, ingest the Enron sample on device, and check the stats sheet shows the expected dimension count. Anything else (quantized outputs, flexible shapes) needs code changes in `CoreMLRunner`.
- **Verified conversion (2026-10-27):** coremltools 9.0 + torch 2.14.1 + transformers 4.36.2,
  fp32, iOS 17. Pooled vectors vs PyTorch: **cosine 1.000000, max |diff| 1.7e-07** on three probe
  texts. On iPhone 15 Pro: load 31 ms warm (175 ms first), **8.3 ms/chunk**, ~4.8 W and
  **34 mJ/chunk** (Power Profiler). Numbers: `docs/benchmarks.md`, `docs/benchmarks_batter.md`.
- Sizes (measured): no runtime dependency — Core ML ships with iOS (0 MB added); the compiled `embedding.mlmodelc` adds ~86 MB. Debug `.app` totals ~91 MB — the lightest install of the three.
- Build/run: `xcodegen generate`, then `open MyLeoVSCoreML.xcodeproj` or `xcodebuild -scheme MyLeoVSCoreML -destination 'platform=iOS Simulator,name=iPhone 17' build`
