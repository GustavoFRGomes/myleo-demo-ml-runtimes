# Julia-1 suite — ONNX Runtime

- Backend: CPU
- Window: 1024 tokens
- Device: iPhone · iOS 27.0.1
- Load: 4543.1 ms total (tokenizer 3862.4 ms, graph 680.7 ms)
- Prompts: 18 · 918.6 ms prompt time · 51.0 ms average · 37.9 ms–72.3 ms
- Wall clock: 919.7 ms
- Battery: 94% → 94% (unplugged) · thermal fair

## Performance by question type

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| choice | 8 | 459.1 ms | 57.4 ms | 37.9 ms | 72.3 ms |
| score | 5 | 251.3 ms | 50.3 ms | 44.6 ms | 58.7 ms |
| noul | 5 | 208.2 ms | 41.6 ms | 38.3 ms | 44.1 ms |

## Prompts

| ID | Type | Tokens | Window | Total | Answer |
|---|---|---:|---:|---:|---|
| route-billing | choice | 62 | 1024 | 59.0 ms | → returns |
| agent-supervisor | choice | 80 | 1024 | 72.3 ms | → stop |
| review-sentiment | choice | 57 | 1024 | 58.7 ms | → mixed |
| language-french | choice | 39 | 1024 | 37.9 ms | → spanish |
| queue-escalation | choice | 57 | 1024 | 59.0 ms | → normal |
| resolution-damaged | choice | 53 | 1024 | 52.8 ms | → refund |
| intent-account | choice | 58 | 1024 | 61.3 ms | → account_change |
| area-battery | choice | 57 | 1024 | 58.2 ms | → battery |
| score-urgency-outage | score | 59 | 1024 | 58.7 ms | → score 2.983 |
| score-review-tone | score | 41 | 1024 | 44.8 ms | → score 0.299 |
| score-complexity-migration | score | 48 | 1024 | 44.6 ms | → score 1.945 |
| score-risk-drop-table | score | 55 | 1024 | 51.7 ms | → score 0.222 |
| score-confidence-session | score | 54 | 1024 | 51.5 ms | → score 0.003 |
| noul-refund | noul | 46 | 1024 | 43.5 ms | → P(true) 0.919 |
| noul-deadline | noul | 40 | 1024 | 38.3 ms | → P(true) 0.658 |
| noul-production-change | noul | 41 | 1024 | 44.1 ms | → P(true) 1.000 |
| noul-seller-answered | noul | 41 | 1024 | 44.0 ms | → P(true) 0.122 |
| noul-account-access | noul | 40 | 1024 | 38.4 ms | → P(true) 0.189 |
