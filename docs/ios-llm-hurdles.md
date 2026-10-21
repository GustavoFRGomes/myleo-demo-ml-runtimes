# iOS LLM — Hurdles and Caveats

What stood between "build the iOS LLM leaves" and the numbers in `docs/benchmarks.md`,
plus the per-runtime limits that make those numbers what they are. Scope: `ios/llm/*`,
measured on iPhone SE 3rd gen (iOS 27.0.1).

## Toolchain

- **`swift-frontend` 6.3.3 hangs in `ClosureLifetimeFixup`.** Any closure that captures a
  `CheckedContinuation` can send the mandatory SIL pass into an endless
  `computeDominatedBoundaryBlocks` loop: 80% CPU, no diagnostics, hours. Confirmed by
  `sample <pid>` on the stuck process, then by bisecting the batch with one `-primary-file`
  at a time. Fixed in `ios/llm/trio` by doing the synchronous section with
  `lock()` / `defer { unlock() }` instead of a `withLock` closure
  (`MiniCPMModelLocator`, `OnnxGenAIModelLocator`, `HuggingFaceModelDownloader`).
  `ios/llm/litert-lm/Sources/Data/HuggingFaceModelDownloader.swift` still has the shape
  and therefore still will not build on this toolchain.
- **The earlier "2.5-hour hang" was a different problem:** SwiftPM stalling on LFS
  payloads of `google-ai-edge/LiteRT-LM`. Build with `GIT_LFS_SKIP_SMUDGE=1` and a warm
  `-clonedSourcePackagesDirPath`; the prebuilt `CLiteRTLM.xcframework` comes from a release
  zip, not LFS.
- **Signing is two teams.** The keychain held a valid Apple Development cert for team
  `68KUU3RQ58`, while the Xcode account that can mint profiles is `Y45474Z24J`. Build with
  `DEVELOPMENT_TEAM=Y45474Z24J` as an override — never in `project.yml`. The first launch
  also needs **Settings → General → VPN & Device Management → Developer App → Trust**;
  until then `devicectl` fails with "profile has not been explicitly trusted".
- **UI test bundles cannot be provisioned here.** `xcodebuild build-for-testing` stalls in
  `GatherProvisioningInputs` and then fails with "No Accounts" for
  `…UITests.xctrunner`, because the account store will not mint a second App ID. That is why
  benchmarking lives in the app as a launch argument (`--benchmark …`) instead of XCUITest.
  There is also no `adb`-equivalent input injection for iOS devices (`idb` is not installed).

## MiniCPM5 ONNX packaging (what the repo does not publish)

The `anydaytv/minicpm5-1b-onnx-int4` repo ships weights + tokenizer only. Three repairs
happen on load, all idempotent and unit-tested:

- **`genai_config.json` is synthesized** (`MiniCPM5GenAIConfig`): 24 layers, 16 heads,
  2 KV heads, `hidden_size` 1536, `context_length` 131072, `past_present_share_buffer: true`,
  and **no `position_ids`** — RoPE is fused into the attention op, so the graph only has
  `input_ids`, `attention_mask` and the past/present tensors.
- **`tokenizer.json` needs a 4-byte repair** (`TokenizerPatch`): ORTX rejects
  `\p{N}+`, so it becomes `\p{N}{1,3}`. Without it the model refuses to load with
  `Invalid regex: \p{N}+`.
- **The chat template is built by hand** (`MiniCPM5ChatTemplate`): leading `<s>` (ORTX does
  not add BOS), ChatML turns, trailing `assistant` + empty `<think>` block to disable
  reasoning. `OgaTokenizerApplyChatTemplate` corrupts the heap in ONNX Runtime GenAI 0.17.0
  (Android saw `Scudo ERROR: invalid chunk state`).

Two engine defects found while porting: `max_length` was `maxTokens + 1024` (over-allocates
the KV cache; now `promptTokens + maxTokens`), and `loadMs` was hard-coded to `0`.

## Accelerators on iOS

| Runtime | What "accelerated" means | ANE? |
|---|---|---|
| LiteRT-LM | `Backend.gpu` → **Metal** (the xcframework embeds Metal kernels); `Backend.cpu` is XNNPACK | **No switch exists.** The Swift API only has `.cpu` and `.gpu`; there is no delegate hook. |
| ONNX GenAI | **CPU only today** (`OgaCreateModel`, no provider appended) | **Possible in principle.** The pod exports `OrtSessionOptionsAppendExecutionProvider_CoreML` and GenAI has `OgaConfigAppendProvider` + `MLComputeUnits`; `CPUAndNeuralEngine` = ANE, `CPUAndGPU` = Core ML's GPU path. Untested. |
| Core ML | `MLModelConfiguration.computeUnits = .cpuAndNeuralEngine` | Yes, requested — but Core ML chooses per op and silently falls back. |

Caveats behind that table:

- **Requesting ANE is not proof of ANE.** The vector-search evidence: the Core ML EP
  delegated only 66/276 nodes of MiniLM, and the delegated graph then failed at invoke —
  CPU fallback. A decoder with fused `GroupQueryAttention` + `MatMulNBits` is a harder
  delegation target, so expect partial coverage at best. The only ground truth is Xcode's
  Core ML instrument during a run.
- **No Metal-direct or WebGPU EP on iOS.** ORT's accelerated path on Apple is the Core ML
  EP; WebGPU symbols in the binary do not make Dawn's iOS backend usable.
- **LiteRT-LM cannot borrow LiteRT's Core ML delegate.** The `.tflite` interpreter has one
  (used by `ios/vector-search/litert`), but LiteRT-LM's `Engine` exposes no delegates, and
  `_gpu_opt` bundles are laid out for Metal.

### Proof that the LiteRT-LM GPU run is real

The app's banner reports the backend it *requested* (`EngineConfig.backend`), so it is not
evidence by itself. The device console is. This is `--benchmark litert-gpu` on the SE,
trimmed to the load path:

```
INFO: [accelerator_registry.cc:54] RegisterAccelerator: ptr=…, name=GPU Metal
INFO: [gpu_registry.cc:113] Statically linked GPU accelerator registered.
I0000 delegate_metal.mm:89] Created a Metal device.
INFO: [gpu_environment.cc:436] Failed to create OpenCL context.      ← expected on iOS
INFO: [gpu_environment.cc:398] Created Metal device from provided device id
INFO: [gpu_environment.h:155] Created LiteRT GpuEnvironment.
I0000 gpu_backend_metal.mm:114] Metal Residency Set successfully initialized.
I0000 delegate_kernel.cc:329] Total 49 external tensors are used for delegate inputs and outputs
I0000 delegate_kernel.cc:979] Initializing Metal-based API from serialized data.
I0000 delegate_kernel.cc:866] Initialized InferenceContext from serialized data.
```

Both graphs (49 and 50 I/O tensors — prefill and decode) go through the Metal delegate, and
no per-op "falling back to CPU" warnings appear. Two footnotes:

- `sampler_factory.cc:572] Metal sampler not available, falling back to statically linked
  C API` — token sampling is CPU-side, which is a few microseconds per token, not a sign of
  model fallback.
- `npu_registry.cc:34] NPU accelerator could not be loaded and registered:
  kLiteRtStatusErrorNotFound` — the engine probes for an NPU and finds none, which is the
  runtime confirmation that there is no ANE path in this web of iOS LiteRT-LM.

## Runtime caveats

- **Core ML's 128-token window has no KV cache.** Every token re-runs a 128-token forward
  pass and reads a 130,560-wide logits row, hence ~2 tok/s. It is a correctness demo, not a
  performance comparison.
- **Context budgets differ by an order of magnitude:** LiteRT-LM 1024 (bundle-declared),
  ONNX 131,072 declared / ~4k practical (fp32 KV at 48 KB/token), Core ML 128.
- **Token accounting:** for LiteRT-LM and ONNX the KV-cache delta includes the prompt
  (533 ≈ 512 output + 21 prompt), matching Android's 544 = 512 + 32; Core ML reports its own
  sampled count. `tok/s` is decode-window only, so this only shifts the number by a few percent.
- **First launch is not steady state.** Metal pipeline compilation cost 5634 ms load and
  959 ms TTFT on the first process ever; Core ML paid 30 s to compile the `.mlpackage`
  (1.5 s afterwards, from cache).
- **Thermal throttling is measurable on this phone.** A CPU batch run straight after
  11 minutes of Core ML read 25.6–26.4 tok/s; after a 10-minute cooldown, 35.5–35.7. Quote
  the cooled numbers, or state the thermal state with the number.
- **No memory numbers.** iOS has no `dumpsys meminfo` equivalent; a footprint requires
  Instruments' Activity Monitor during a run and would not be comparable to Android PSS.

## Still open

- `ios/llm/litert-lm` needs the same `CheckedContinuation` fix before it will build.
- `android/llm/dual` still runs Phi-4-mini behind its picker.
- Android `vector-search/litert` (BERT 512-d) is still `TBD` in `docs/benchmarks.md`.
- ONNX on ANE / Core ML GPU is an unrun experiment, not a supported path.
