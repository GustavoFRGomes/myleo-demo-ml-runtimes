# Benchmarks — Battery and Energy

Companion to [`docs/benchmarks.md`](benchmarks.md) (speed) and
[`docs/benchmarks-decision.md`](benchmarks-decision.md) (decision suite). This page records
what each demo actually draws, how it was measured, and where the numbers stop being
trustworthy.

Interactive view of all of these numbers: [`benchmarks.html`](benchmarks.html).

Reference devices: **Samsung `SM-S938B`** (Android 17, 12 GB) and **iPhone SE 3rd gen**
(iOS 27.0.1). Every measurement below was taken **unplugged** — a charging phone cannot
discharge, so any battery/energy number taken on power is meaningless.

## What each platform exposes

| | Android | iOS |
|---|---|---|
| Instantaneous current | `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)` (µA, in-app) — what the decision leaves sample. `/sys/class/power_supply/*` is **permission-denied** without root. | Not exposed to apps. |
| Energy accounting | `dumpsys batterystats` per-UID mAh (power-profile model); battery charge counter (µAh, updates in ~4.9 mAh steps); battery %. | `UIDevice.batteryLevel` only — quantised in ~5% steps and updated late. |
| Fine-grained signal | Per-UID estimate (blind to GPU work); charge-counter delta is the honest whole-device figure. | `xctrace` **Power Profiler**, `SystemPowerLevel` table: `power-usage` in **%/hour**, sampled ~1 s. |

Both platforms produce **model-based estimates, not meter readings**; compare runtimes on the
same device, never across devices.

## Android — vector search (`android/vector-search/dual`, `SM-S938B`)

Workload: 120 s of the real embedder in a loop (`--ei bench_seconds 120`, no DB writes, screen
held on), `dumpsys batterystats --reset` before each run, whole-device charge counter as the
primary metric because the per-UID estimate cannot see GPU work.

| Config | Charge counter / 120 s | ≈ per 1000 chunks | ≈ energy per chunk | Throughput |
|---|---|---|---|---|
| idle (app foreground) | 4.9 mAh | — | — | — |
| ONNX CPU (XNNPACK) | 64.0 mAh | ~11–12 mAh | ~160–180 mJ | 39.8–45.4 chunks/s |
| **ONNX WebGPU** | **54.2 / 29.6 mAh** | **~3–6 mAh** | **~40–80 mJ** | **73.3–74.9 chunks/s** |

- WebGPU is ~1.7× faster and does the same work for roughly **2–3× less energy per chunk**.
- The per-UID estimate reported **4.6 mAh** for a run the pack actually supplied 30–54 mAh for;
  it models CPU time only. Charge-counter deltas are used above for that reason.
- Counter resolution is ~4.9 mAh, so each cell is ±1 step; the CPU/GPU gap is far outside it,
  the two WebGPU runs are not distinguishable from each other.
- Full detail, including the thermal behaviour (CPU throttled 19.2 → 25.2 ms/chunk, WebGPU
  held 13.4): [`docs/benchmarks.md`](benchmarks.md#accelerated--onnx-webgpu-android-sm-s938b-2026-10-21).

## Android — decision suite (`android/decision/*`, `SM-S938B`)

The suite runs 18 prompts in ~1.4 s per lane, which is **below the gauge's resolution**: both
lanes report `77% → 77%` and a `0.000 mAh` charge-counter delta. What the in-app
`BatteryManager` sampling does capture is the instantaneous current at the end of the run
(negative = discharging):

| Lane | Level | Charge Δ | Current at end |
|---|---|---:|---:|
| LiteRT (GPU FP32) | 77% → 77% | 0.000 mAh | −2755 mA |
| ONNX (CPU, 4 threads) | 77% → 77% | 0.000 mAh | −4921 mA |

Those snapshots are taken while the graph is still settling, not averaged over the suite; for
energy-per-decision, run the suite back-to-back and divide the accumulated charge delta by the
number of decisions. Full tables and procedure:
[`docs/benchmarks-decision.md`](benchmarks-decision.md#battery-and-energy).

## iOS — LLM (`ios/llm/trio`, iPhone SE 3rd gen)

The battery percentage is unusable for a single generation: a 77 s run read **85% → 80%**
while the power model accounted for only ~1.2% of the pack. `xctrace` **Power Profiler** was
attached to the running process and its `SystemPowerLevel` table exported (one `%/hour` sample
per second). Five generations per runtime, same prompt and params, screen held on,
brightness ~20%, thermal state `fair` after the preceding runs.

Energy converted with the SE 3 pack: **2018 mAh, 3.86 V nominal**.

| Runtime | tok/s (this run) | mean %/hr | peak %/hr | ≈ power | 5-run energy | ≈ mJ/token |
|---|---|---|---|---|---|---|
| `onnx-genai` CPU | 43.5 | **78.0** | 100.0 | **6.1 W** | 331 J | 130 |
| `litert-lm` GPU (Metal) | 35.9 | **59.9** | 104.1 | **4.7 W** | 325 J | 122 |
| `litert-lm` CPU (XNNPACK) | 29.2 | **48.0** | 82.9 | **3.7 W** | 185 J | 133 |

- **Energy per token is flat (122–133 mJ) across all three runtimes.** ONNX is fastest and
  draws the most; LiteRT CPU is slowest and draws the least; per token they converge. On this
  phone the runtime choice buys wall time, not energy efficiency.
- The `fair` thermal state explains the lower tok/s versus the cooled table in
  [`docs/benchmarks.md`](benchmarks.md) (LiteRT GPU 35.9 here vs 43.8 cooled).
- `%`-based battery deltas are not usable at this run length; the in-app fields are still
  recorded (`batteryStartPct`, `batteryDeltaPct`, `energyMWhEstimate`) for long runs.

## Decision suite (iOS, iPhone 15 Pro)

The 18-prompt decision suite is far too short to move the iOS battery gauge: 4× the suite is
36 s on LiteRT and 3 s on ONNX, and the in-app reading stayed **80% → 80%** in both cases.
iOS does not expose battery current or wattage to apps, so a percentage delta is the only
battery-level signal available — and it is quantised in ~5% steps, i.e. useless at this length.

The energy figures below come from `xctrace` **Power Profiler** attached to the run
(`SystemPowerLevel`, mean %/hour), converted with the SE/15 Pro-class pack assumption
(2018 mAh, 3.86 V). They are model-based, not a meter.

| Lane (4× suite = 72 prompts) | Wall | mean %/hr | ≈ power | Energy | ≈ per prompt |
|---|---:|---:|---:|---:|---:|
| LiteRT CPU (window 512) | 36.4 s | 58.9 | 4.6 W | 146 J | **2033 mJ** |
| ONNX CPU (window 1024) | 3.0 s | 67.6 | 5.3 W | 10 J | **136 mJ** |

Caveats: the ONNX window is only ~2 s of samples (three points), so its figure is indicative;
LiteRT's is the more reliable of the two. The per-prompt energy tracks the 13× speed gap almost
exactly — the runtimes draw similar power but ONNX spends far less time doing it.

## Reproduce

Android vector search:

```bash
adb shell dumpsys batterystats --reset
adb shell am start -n org.ggomes.myleo.vectorsearch.dual/.MainActivity \
  --es onnx_ep webgpu --ei bench_seconds 120
# after it finishes:
adb shell dumpsys battery | grep -E "level|Charge counter"
adb shell dumpsys batterystats --charged org.ggomes.myleo.vectorsearch.dual | grep "UID u"
```

Android decision suite: run the suite in the app and export the report — it already contains
`batteryStartPct`, `batteryEndPct`, `chargeDeltaMilliAmpHours` and `currentNowUa`.

iOS:

```bash
xcrun devicectl device process launch --device <udid> \
  org.ggomes.myleo.llm.trio --benchmark litert-gpu --bench-repeats 5
xcrun xctrace record --template 'Power Profiler' --device <udid> \
  --attach MyLeoLLMTrio --time-limit 120s --output run.trace
xcrun xctrace export --input run.trace \
  --xpath '/trace-toc/run[@number="1"]/data/table[@schema="SystemPowerLevel"]'
```

## Limitations

- **Resolution:** Android charge counter ~4.9 mAh steps; iOS battery gauge ~5% and delayed.
  Anything shorter than a few minutes is invisible to the gauge — use the loop / repeat count.
- **Model, not meter:** Android's `batterystats` and iOS's `Power Profiler` both estimate from
  subsystem activity (CPU/GPU/display/network), with per-device coefficients.
- **Display is included** in every number (screen on at ~20% brightness on iOS; default
  brightness on Android). It is constant across configs, so comparisons hold, absolutes do not.
- **Thermals matter:** sustained runs throttle; the measured tok/s in power runs is lower than
  cold-start numbers, and power draw does not rise to compensate.
- **GPU attribution:** Android's per-UID mAh does not see GPU work; iOS exposes no per-process
  wattage at all. Use whole-device deltas or subsystem impact tables.
