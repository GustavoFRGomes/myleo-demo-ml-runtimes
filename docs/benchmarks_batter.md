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

### Sustained battery runs (60 generations per lane)

Because Power Profiler needs the cable and reads zero while charging, the long-run checks use
the gauge instead: 60 back-to-back generations unplugged, ~11–17 minutes per lane.

| Runtime | Wall | Battery | Thermal | Sustained tok/s | ≈ power | ≈ per token |
|---|---:|---|---:|---:|---:|---:|
| LiteRT-LM GPU | 1044.5 s | 95% → 80% | serious | 32.4 | 4.0 W | 132 mJ |
| ONNX GenAI CPU | 1052.1 s | 80% → 60% | serious | 30.8 | 5.3 W | 180 mJ |
| LiteRT-LM CPU | 687.2 s | 55% → 40% | serious | 27.3 | 6.1 W | 252 mJ |

The Power Profiler table above remains the better per-token reference (122–133 mJ at `fair`);
these runs show what ten-plus minutes of heat does instead — every lane throttles to 27–32
tok/s and the gauge step makes the mJ column an estimate, not a measurement. CPU's run is the
shortest and therefore the least reliable.

## LLM (iOS, iPhone 15 Pro, battery runs)

Ten-minute runs on battery (60 generations each), because the gauge cannot resolve the usual
3-run cells. Deltas are 5%-quantised; the ranges below are derived from that step, not noise.

| Runtime | Wall | Battery | Thermal | ≈ power | ≈ per token |
|---|---:|---|---:|---:|---:|
| LiteRT-LM GPU | 750.8 s | 70% → 65% | critical | 1.9 W (1.9–3.7) | 44 mJ (44–88) |
| ONNX GenAI CPU | 674.7 s | 60% → 50% | serious | 4.2 W (2.1–6.2) | 97 mJ (49–146) |

Both lanes throttle from ~65 tok/s cold to ~46–48 tok/s sustained. iOS offers no current/wattage
API and Power Profiler reports zero while charging, so battery percentage over long runs is the
only unplugged signal available.

## Decision suite (iOS, iPhone 15 Pro)

The 18-prompt decision suite is short — 4× is 36 s on LiteRT but only 3 s on ONNX — and the
in-app battery reading stayed **80% → 80%** in every run. iOS does not expose battery current or
wattage to apps, so the percentage is the only battery-level signal available, and it is
quantised in ~5% steps: it cannot resolve a workload of seconds.

Energy therefore comes from `xctrace` **Power Profiler** (`SystemPowerLevel`, ~1 sample/second,
mean %/hour), converted with the 2018 mAh / 3.86 V pack assumption. The sampling rate is the
catch: a run must last **tens of seconds** before the window means anything.

| Lane | Repeats | Prompts | Wall | Sampling window | mean %/hr | ≈ power | Energy | ≈ per prompt |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| LiteRT | 4× | 72 | 36.4 s | ~32 s | 58.9 | 4.6 W | 146 J | **2033 mJ** |
| ONNX | 4× | 72 | 3.0 s | **~2 s** | 67.6 | 5.3 W | 10 J | 136 mJ — *indicative* |
| ONNX | 10× | 180 | 6.6 s | **~3 s** | 73.9 | 5.8 W | 17 J | 94 mJ — *indicative* |
| ONNX | 40× | 720 | 28.6 s | **~28 s** | 61.5 | 4.8 W | 134 J | **186 mJ** |

- **The 4× ONNX figure was not a measurement.** With ~2 s of samples the mean is dominated by
  attach timing and the clock ramp; 10× (3 s) is barely better. Only the 40× run gives a window
  comparable to LiteRT's.
- With a proper window, ONNX costs **~186 mJ per decision** versus LiteRT's **~2033 mJ** — the
  ~11× gap is mostly the 13× difference in time, since both lanes draw roughly 4.5–5 W.
- **Battery level is unusable here on iOS**: no current/wattage API, ~5% gauge steps, and the
  runs last seconds to a minute. That is a platform limitation, not a missing measurement.


## Embedding (iOS, iPhone 15 Pro)

Run through `ios/vector-search/trio --bench-embedding`, 20-chunk corpus repeated 40× (LiteRT) and
80× (ONNX) to give Power Profiler a ~31–35 s window. Battery read 80% → 80% in both lanes — the
gauge cannot resolve this workload and iOS exposes no current API.

| Runtime | Chunks | Median ms/chunk | mean %/hr | ≈ power | Energy | ≈ per chunk |
|---|---:|---:|---:|---:|---:|---:|
| LiteRT | 800 | 44.3 | 47.3 | 3.7 W | 129 J | **161 mJ** |
| ONNX | 1600 | 20.5 | 54.2 | 4.2 W | 130 J | **82 mJ** |
| Core ML | 3600 | 8.8 | 61.3 | 4.8 W | 124 J | **34 mJ** |

ONNX runs at about twice LiteRT's speed for roughly the same wall power; Core ML is faster still
and cheapest per chunk. Load timings for LiteRT/ONNX include the Core ML delegate/EP attempt that
falls back to CPU; Core ML loads its compiled `mlmodelc` directly.

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
