# Julia-1 suite — LiteRT

- Backend: CPU
- Window: 512 tokens
- Device: iPhone · iOS 27.0.1
- Load: 3657.0 ms total (tokenizer 3590.6 ms, graph 65.4 ms)
- Prompts: 18 · 11888.4 ms prompt time · 660.5 ms average · 611.5 ms–680.7 ms
- Wall clock: 11891.0 ms
- Battery: 94% → 94% (unplugged) · thermal fair

## Performance by question type

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| choice | 8 | 5159.0 ms | 644.9 ms | 611.5 ms | 660.3 ms |
| score | 5 | 3340.7 ms | 668.1 ms | 661.2 ms | 675.3 ms |
| noul | 5 | 3388.7 ms | 677.7 ms | 675.1 ms | 680.7 ms |

## Prompts

| ID | Type | Tokens | Window | Total | Answer |
|---|---|---:|---:|---:|---|
| route-billing | choice | 62 | 512 | 611.5 ms | → returns |
| agent-supervisor | choice | 80 | 512 | 637.6 ms | → stop |
| review-sentiment | choice | 57 | 512 | 640.4 ms | → mixed |
| language-french | choice | 39 | 512 | 643.3 ms | → spanish |
| queue-escalation | choice | 57 | 512 | 655.1 ms | → normal |
| resolution-damaged | choice | 53 | 512 | 653.8 ms | → refund |
| intent-account | choice | 58 | 512 | 657.1 ms | → account_change |
| area-battery | choice | 57 | 512 | 660.3 ms | → battery |
| score-urgency-outage | score | 59 | 512 | 665.0 ms | → score 2.983 |
| score-review-tone | score | 41 | 512 | 661.2 ms | → score 0.298 |
| score-complexity-migration | score | 48 | 512 | 666.7 ms | → score 1.944 |
| score-risk-drop-table | score | 55 | 512 | 672.4 ms | → score 0.221 |
| score-confidence-session | score | 54 | 512 | 675.3 ms | → score 0.003 |
| noul-refund | noul | 46 | 512 | 675.1 ms | → P(true) 0.919 |
| noul-deadline | noul | 40 | 512 | 675.4 ms | → P(true) 0.659 |
| noul-production-change | noul | 41 | 512 | 678.4 ms | → P(true) 1.000 |
| noul-seller-answered | noul | 41 | 512 | 680.7 ms | → P(true) 0.122 |
| noul-account-access | noul | 40 | 512 | 679.0 ms | → P(true) 0.188 |
