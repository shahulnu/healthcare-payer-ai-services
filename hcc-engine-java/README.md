# HCC Evidence Finder engine (Java)

A Spring Boot port of the HCC Evidence Finder in the site project's `src/lib/hcc/engine.ts`. It returns the same JSON as the TypeScript engine, so the Next.js demo can use either one. It's part of this multi-module project. See [../README.md](../README.md) for the build, run commands and shared settings.

## How it works

Claude reads the chart, and code owns the rules.

| Class | Role |
|---|---|
| `HccEngine` | Redacts the chart, then gets Claude's review as structured output, or from a recording |
| `Reconciler` | Decides every disposition, using the rules below |
| `HccData`, `HccController` | Load the shared charts and map; same endpoints as the Next.js route |

**The rules in `Reconciler`:**
- Support needs a verified quote from an acceptable encounter, so a nurse phone call can't count.
- Codes that mean active disease can't rest on "history of" language.
- The HCC and the hierarchy come from `hcc-map.json`, never from the model.
- A submitted code the model skipped still goes to a coder.
- Unknown assessments are treated as needing a person.
- Deletions sort first.

Nothing is submitted or deleted automatically. A certified coder decides every line.

Charts, the HCC map and recorded reviews come from `../data/hcc/*.json`.

**Check the map:** `hcc-map.json` is a small demo subset of CMS-HCC V28, checked against published coding guides rather than the CMS mapping file. Verify it against the CMS tables for the payment year before relying on it.

## Run

After `mvn install` in the project root, run this from this folder:

```bash
mvn spring-boot:run
```

| Endpoint (port 8085) | Purpose |
|---|---|
| `GET /api/hcc/review` | `{ liveAvailable, engine }` |
| `POST /api/hcc/review` | Body `{ "memberId": "M-70112", "mode": "replay" \| "live" }` |
| `GET /api/hcc/members` | The demo charts |
| `GET /actuator/health` | Health check |

To use it from the demo, set `HCC_ENGINE_URL=http://localhost:8085` in the site project's `.env.local`. The run trace then shows "Java engine". A live review takes about 15 seconds and 3 cents.

## Tests

`HccEngineTest` (19 tests) covers:
- support from a verified quote in an acceptable encounter
- the phone-call rule, invented quotes, and "history of" against active-disease codes
- the hierarchy, unknown codes, no-HCC codes, skipped submitted codes and unknown assessments
- deletion-first ordering
- a replay of the recorded reviews, checked against `evals/hcc-labels.json`, including the hidden instruction and the BMI with no diagnosis

## Toward production

- **Tables:** load the full CMS V28 mapping and hierarchy tables, versioned by payment year.
- **Encounter rules:** derive acceptability from CMS rules on provider type and service, not a flag on the document.
- **Chart intake:** read charts from retrieval vendors and EHR feeds, with OCR and signature and date-of-service validation.
- **Corrections:** send deletes through the plan's overpayment and data-correction process, with an audit trail from quote to coder decision.
