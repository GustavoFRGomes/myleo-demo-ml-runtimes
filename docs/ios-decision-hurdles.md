# iOS Decision (Julia-1) — Hurdles and Caveats

What the iOS decision port had to solve, and what it still can't do. Companion to
[`docs/benchmarks-decision.md`](benchmarks-decision.md) (the Android numbers and suite
contract) and [`docs/ios-llm-hurdles.md`](ios-llm-hurdles.md) (toolchain/signing issues shared
with the LLM leaves).

Leaves: `ios/decision/litert`, `ios/decision/onnx`, `ios/decision/trio` (Core ML slot reserved,
not implemented).

## Model packaging

Both Android packages are runtime exports of the same checkpoint, but neither is complete for
Swift consumption:

- **The LiteRT package has no `tokenizer_config.json`.** `swift-transformers` loads
  `tokenizer.json` together with a config, so the app also fetches the 652-byte file from the
  base checkpoint, `SupersonicLabs/Julia-1/tokenizer/tokenizer_config.json`. Same tokenizer,
  different repo.
- **The ONNX package needs its external weights next to the graph.** `model.onnx` is only 3 MB;
  the 577 MB `model.onnx.data` must sit beside it or the session fails to load. The downloader
  treats the pair as one unit.
- Package sizes on disk: **381 MB** for LiteRT (512 graph, 196 MB fp16 table, tokenizer) and
  **614 MB** for ONNX. Installed with `devicectl device copy to` for testing; the app-facing
  downloader is the next piece.

## LiteRT (TFLite 2.17 on iOS)

- **Tensor names are signature-qualified.** The graph exposes
  `serving_default_inputs_embeds`, `serving_default_attention_mask`,
  `serving_default_qtype_onehot`, and the output is renamed
  `serving_default_token_logits_output` — not the plain names the Android `CompiledModel` path
  uses. The runner matches by suffix, and falls back to the single output when there is only
  one, logging the real name.
- **Metal cannot compile this graph — at any precision.** `MetalDelegate` with precision loss
  disabled (FP32, as the contract requires) and also with `isPrecisionLossAllowed = true`
  (fp16) both throw `Failed to create the interpreter.`, so this is not a precision problem but
  a delegate/graph incompatibility in the 2.17 pod. The engine logs it and falls back to CPU,
  reporting `ran=CPU` — it does not silently claim GPU. Consequence: the iOS LiteRT lane is
  **CPU-only** today; the A→C check (GPU vs CPU answers) reduced to CPU vs CPU,
  `sameChoice=true`, `maxProbDiff=0.000000`.
- **The Core ML delegate cannot create the interpreter either.** Tried with
  `TensorFlowLiteSwift/CoreML` and `CoreMLDelegate.Options.enabledDevices = .all` (the same
  delegate the vector-search trio uses for ANE): `Failed to create the interpreter.` So this is
  a delegate/graph incompatibility, not a Metal-specific one. The trio's Core ML runtime is a
  *native* `.mlpackage` engine, not a TFLite delegate, so it offers no shortcut here — and no
  Julia-1 Core ML export exists.
- **XNNPACK is off by default in the TFLite Swift pod.** `isXNNPackEnabled` defaults to `false`;
  enabling it changed nothing here (452 → 455 ms), i.e. the reference-kernel hypothesis was
  wrong and this graph is compute-bound on the CPU path. Android's 76 ms is the **GPU FP32**
  lane, so the honest comparison is Android GPU vs iOS CPU until iOS has a GPU path.
- **Threads matter a lot.** The default interpreter thread count produced ~689 ms per decision
  (512 window, 45 tokens); `options.threadCount = 4` (matching Android's 4 intra-op threads)
  brings it to ~452 ms (median of three device runs). Still ~6× the Android LiteRT figure, which runs on the newer LiteRT
  `CompiledModel` + XNNPACK stack rather than the older TFLite pod.
- **The token table is fp16 and 196 MB.** It is memory-mapped, never copied; rows are gathered
  into a `[window × 384]` float32 buffer, and padded positions gather the real `<pad>` row (id
  0), matching the author's NumPy lookup.

## ONNX Runtime (1.30, ObjC + C API)

- **The ObjC wrapper cannot express the graph's `marker_mask` input.** The model declares
  `marker_mask` as `tensor(bool)`, and `ORTTensorElementDataType` in the ObjC API has no bool:

  ```
  Undefined, Float, Int8, UInt8, Int32, UInt32, Int64, UInt64, String
  ```

  The runner therefore uses the **C API** (`onnxruntime_c_api.h`, bundled through the
  `onnxruntime-c` pod) with `ONNX_TENSOR_ELEMENT_DATA_TYPE_BOOL`. The ObjC API stays imported
  for the rest of the project, so the bridging header carries both.
- **C API ownership is manual.** Every call returns an `OrtStatus` that must be checked and
  released; each input tensor is created per call and released after `Run`; input/output names
  are C strings. The Swift wrapper centralises that in two helpers.
- **Input names, shapes and types are fixed** by the export: `input_ids`/`attention_mask`
  int64 `[1, T]` (T padded up to a multiple of 8), `marker_pos` int64 `[1, N]`,
  `marker_mask` bool `[1, N]`, `qtype` int64 `[1]`; output `logits` float `[1, N]`. Marker
  positions stay absolute because padding is appended, never inserted.
- **CPU only**, like Android: the published export targets browser WebGPU and no iOS/Android
  EP for this graph has been validated.

## Shared runtime notes

- **One request = one forward pass.** No streaming, no KV cache; `Decider` exposes only
  `load` + `decide`, which keeps the suite/report shape different from the LLM leaves.
- **Strict encoding, never truncation.** Requests that do not fit (512/1024 window, 48 tokens
  per option) throw; the ONNX lane uses the 1024 window and 512 head budget, the LiteRT lane
  the 512 graph by default and the 1024 graph when installed.
- **The same tokenizer file** (sha256 `609d8f4c…`) in both packages means an accepted request
  produces identical token ids on either runtime — the basis for the answer-for-answer
  comparison.
- **Core ML is deliberately absent.** There is no Julia-1 `.mlpackage`; the trio lists the
  runtime as unavailable with that reason instead of shipping a dead card.

## Reproduce a decision benchmark on device

```bash
# LiteRT (GPU attempted, CPU fallback; prints both lanes and the comparison)
xcrun devicectl device process launch --device <udid> --console \
  org.ggomes.myleo.decision.litert --bench-decision

# ONNX (C API runner)
xcrun devicectl device process launch --device <udid> --console \
  org.ggomes.myleo.decision.onnx --bench-decision
```

Both harnesses call `exit(0)` after printing — the app disappearing is the benchmark ending,
not a crash.

## LiteRT 2.3 (CLiteRT) integration attempt

LiteRT v2.3.0 publishes iOS binaries (`CLiteRT.xcframework`, `LiteRtMetalAccelerator.xcframework`),
integrated here as a local SPM package (`Packages/LiteRTBinary`) with release URLs + checksums —
no binaries committed. The graph loads and produces identical answers.

What went wrong:

- **The Metal accelerator does not register.** It is embedded in the app bundle, but not linked
  into the binary (no reference pulls it in), and LiteRT's loader tries
  `libLiteRtMetalAccelerator.dylib` / `LiteRtMetalAccelerator` and reports
  `GPU accelerator could not be loaded and registered`. The engine therefore reports a CPU run;
  an earlier "GPU" label was the *requested* accelerator, not the actual one.
- **The 2.3 CPU path is slower than 2.17 for this graph** (709 vs 452 ms), and the 2.3 GPU path
  falls back, so nothing beat the older pod. The 2.3 runner stays in the leaf behind
  `--litert23`; the default is 2.17.
- **Reporting discipline:** do not label a run with the requested accelerator. The 2.17 lane
  reports what its own creation path actually produced; the 2.3 lane cannot yet prove GPU
  placement, so its GPU result is not claimed.

Next if this is revisited: force-link the accelerator (`-framework LiteRtMetalAccelerator`),
or provide it as `libLiteRtMetalAccelerator.dylib` in the framework search path, then re-measure
before changing the default.
