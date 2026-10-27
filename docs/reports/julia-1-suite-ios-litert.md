# Julia-1 suite — LiteRT

- Backend: CPU
- Window: 512 tokens
- Device: iPhone · iOS 27.0.1
- Load: 2670.1 ms total (tokenizer 2630.1 ms, graph 38.7 ms)
- Prompts: 18 · 8575.3 ms prompt time · 476.4 ms average · 451.5 ms–488.8 ms
- Wall clock: 8576.6 ms
- Battery: 80% → 80% (unplugged) · thermal nominal

## Performance by question type

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| choice | 8 | 3741.2 ms | 467.7 ms | 451.5 ms | 480.6 ms |
| score | 5 | 2404.0 ms | 480.8 ms | 477.1 ms | 483.7 ms |
| noul | 5 | 2430.1 ms | 486.0 ms | 482.6 ms | 488.8 ms |

## Prompts

| ID | Type | Tokens | Window | Total | Answer |
|---|---|---:|---:|---:|---|
| route-billing | choice | 62 | 512 | 451.5 ms | → returns |
| agent-supervisor | choice | 80 | 512 | 461.5 ms | → stop |
| review-sentiment | choice | 57 | 512 | 460.7 ms | → mixed |
| language-french | choice | 39 | 512 | 463.7 ms | → spanish |
| queue-escalation | choice | 57 | 512 | 469.1 ms | → normal |
| resolution-damaged | choice | 53 | 512 | 475.1 ms | → refund |
| intent-account | choice | 58 | 512 | 479.1 ms | → account_change |
| area-battery | choice | 57 | 512 | 480.6 ms | → battery |
| score-urgency-outage | score | 59 | 512 | 478.7 ms | → score 2.983 |
| score-review-tone | score | 41 | 512 | 477.1 ms | → score 0.298 |
| score-complexity-migration | score | 48 | 512 | 481.8 ms | → score 1.944 |
| score-risk-drop-table | score | 55 | 512 | 482.7 ms | → score 0.221 |
| score-confidence-session | score | 54 | 512 | 483.7 ms | → score 0.003 |
| noul-refund | noul | 46 | 512 | 482.6 ms | → P(true) 0.919 |
| noul-deadline | noul | 40 | 512 | 485.7 ms | → P(true) 0.659 |
| noul-production-change | noul | 41 | 512 | 487.0 ms | → P(true) 1.000 |
| noul-seller-answered | noul | 41 | 512 | 485.9 ms | → P(true) 0.122 |
| noul-account-access | noul | 40 | 512 | 488.8 ms | → P(true) 0.188 |
