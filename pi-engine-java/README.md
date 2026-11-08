# Payment Integrity Radar engine (Java)

A Spring Boot port of Payment Integrity Radar in the site project's `src/lib/pi/`. It returns the same JSON as the TypeScript engine, so the Next.js demo can use either one. It's part of this multi-module project. See [../README.md](../README.md) for the build, run commands and shared settings.

## How it works

Statistics detect the leads, and Claude only explains them.

| Class | Role |
|---|---|
| `PeerScreen` | Detection, with no model involved: robust z-scores (median and MAD) within each specialty, a Theil-Sen risk adjustment for the 99215 share, a hard rule for more than 16 billed hours in a day, and scores, tiers and exposure estimates |
| `PiEngine` | Turns screen results into facts (provider-level aggregates, no member data), then gets the brief from Claude as structured output, or from a recording |
| `BriefChecker` | Holds the brief if a figure isn't in the facts, wording implies wrongdoing, or citations don't resolve or don't include a flagged pattern |
| `PiData`, `PiController` | Load the shared cohort and screen it at startup; same endpoints as the Next.js routes |

The tier sets the action and the record sample size, and the model can't change them. A held brief goes to an analyst to edit, while the lead stands on the statistics.

**Rounding:** `PeerScreen.fixed()` uses exact binary values with ties away from zero, the same as JavaScript's `toFixed`. `String.format` can differ on values such as 1.005. This keeps z-scores and the fact text the same in both engines, and that matters because the number check compares the brief against that text.

The cohort and recorded briefs come from `../data/pi/*.json`.

## Run

After `mvn install` in the project root, run this from this folder:

```bash
mvn spring-boot:run
```

| Endpoint (port 8084) | Purpose |
|---|---|
| `GET /api/pi/leads` | The full screen: leads, cleared providers, peer values and the risk line |
| `GET /api/pi/brief` | `{ liveAvailable, engine }` |
| `POST /api/pi/brief` | Body `{ "providerId": "PRV-SYN-1017", "mode": "replay" \| "live" }` |
| `GET /actuator/health` | Health check |

To use it from the demo, set `PI_ENGINE_URL=http://localhost:8084` in the site project's `.env.local`. Briefs then come from this service, and the run trace shows "Java engine". The demo page still draws its queue and charts from the TypeScript screen, which gives identical results on this cohort. A live brief takes about 15–20 seconds and 3 cents.

## Tests

`PiEngineTest` (22 tests) covers:
- median, robust z-score and Theil-Sen, including that an outlier can't tilt the line
- rounding parity with JavaScript
- scores, tiers and exposure against the TypeScript results
- the decoy being cleared and the hard rule
- each brief check: invented figures, accusatory wording and citations
- fact wording
- a replay of all 6 briefs, checked against `evals/pi-labels.json`

## Toward production

- **Batch screen:** run `PeerScreen` as a scheduled job on the claims warehouse, by specialty and region, with billing and rendering NPIs separated. Don't run it at startup.
- **Trained models:** train them in Python on closed SIU cases, and run predictions in Java with XGBoost4J or ONNX alongside these explainable rules.
- **Case management:** integrate with it, keeping an audit trail from statistic to decision.
