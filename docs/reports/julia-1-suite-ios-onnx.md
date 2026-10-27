# Julia-1 suite — ONNX Runtime

- Backend: CPU
- Window: 1024 tokens
- Device: iPhone · iOS 27.0.1
- Load: 3031.8 ms total (tokenizer 2712.3 ms, graph 319.5 ms)
- Prompts: 18 · 662.8 ms prompt time · 36.8 ms average · 26.7 ms–53.8 ms
- Wall clock: 664.1 ms
- Battery: 80% → 80% (unplugged) · thermal nominal

## Performance by question type

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| choice | 8 | 333.7 ms | 41.7 ms | 26.7 ms | 53.8 ms |
| score | 5 | 180.9 ms | 36.2 ms | 31.2 ms | 42.6 ms |
| noul | 5 | 148.2 ms | 29.6 ms | 26.8 ms | 31.5 ms |

## Prompts

| ID | Type | Tokens | Window | Total | Answer |
|---|---|---:|---:|---:|---|
| route-billing | choice | 62 | 1024 | 44.7 ms | → returns |
| agent-supervisor | choice | 80 | 1024 | 53.8 ms | → stop |
| review-sentiment | choice | 57 | 1024 | 44.5 ms | → mixed |
| language-french | choice | 39 | 1024 | 26.7 ms | → spanish |
| queue-escalation | choice | 57 | 1024 | 42.9 ms | → normal |
| resolution-damaged | choice | 53 | 1024 | 37.7 ms | → refund |
| intent-account | choice | 58 | 1024 | 42.1 ms | → account_change |
| area-battery | choice | 57 | 1024 | 41.4 ms | → battery |
| score-urgency-outage | score | 59 | 1024 | 42.6 ms | → score 2.983 |
| score-review-tone | score | 41 | 1024 | 31.2 ms | → score 0.299 |
| score-complexity-migration | score | 48 | 1024 | 32.2 ms | → score 1.945 |
| score-risk-drop-table | score | 55 | 1024 | 37.4 ms | → score 0.222 |
| score-confidence-session | score | 54 | 1024 | 37.5 ms | → score 0.003 |
| noul-refund | noul | 46 | 1024 | 31.4 ms | → P(true) 0.919 |
| noul-deadline | noul | 40 | 1024 | 26.8 ms | → P(true) 0.658 |
| noul-production-change | noul | 41 | 1024 | 31.5 ms | → P(true) 1.000 |
| noul-seller-answered | noul | 41 | 1024 | 31.0 ms | → P(true) 0.122 |
| noul-account-access | noul | 40 | 1024 | 27.6 ms | → P(true) 0.189 |
