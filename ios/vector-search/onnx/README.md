# iOS Vector Search — ONNX

- Runtime: `onnxruntime-objc` `~> 1.30.0` via CocoaPods (ObjC `ORTSession` API called from Swift, CPU; Core ML execution provider later)
- Model format: `.onnx` MiniLM sentence model (Int64 `input_ids`/`attention_mask`/`token_type_ids` in, float `last_hidden_state` out, mean-pooled + L2-normalized in code) + WordPiece `vocab.txt` (same vocabulary text as BERT)
- Scope contract:
  - Tokenize + embed text → normalized float vector
  - On-device index with top-k cosine search + scores
  - Report embed ms + search ms
  - Minimal SwiftUI demo: ingest docs, query, ranked hits
- Model: `all-MiniLM-L6-v2` (Apache-2.0, ~90 MB) — https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx → bundle as `embedding.onnx`; vocab from https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt → bundle as `vocab.txt` (byte-identical to the BERT vocab). 384-d output. Binaries are never committed.
- Replacing the model (2 files in `Sources/Resources/`):
  1. `embedding.onnx` — any transformer sentence model, BUT tensor names are hardcoded in `OnnxRunner`: Int64 inputs must be named exactly `input_ids`/`attention_mask`/`token_type_ids`, output exactly `last_hidden_state` (float `[1 x seqLen x dim]`). Sequence length must equal `maxSeqLen` (default 128). Differently-named tensors need a code change.
  2. `vocab.txt` — must be the training vocab of that exact model (one token per line, `[PAD]`/`[UNK]`/`[CLS]`/`[SEP]` present).
  3. Rebuild, ingest the Enron sample on device, and check the stats sheet shows the expected dimension count.
- Sizes (measured): runtime pod `onnxruntime-objc` (~128 KB ObjC) + `onnxruntime-c` (~243 MB with all-arch binaries) adds ~36 MB to the installed app — the heaviest of the three runtimes; model adds ~90 MB. Debug `.app` totals ~127 MB.
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoVSOnnx.xcworkspace` (always the workspace). Tests: `xcodebuild -workspace MyLeoVSOnnx.xcworkspace -scheme MyLeoVSOnnx -destination 'platform=iOS,id=<DEVICE_UDID>' test` (+ signing overrides for devices).
