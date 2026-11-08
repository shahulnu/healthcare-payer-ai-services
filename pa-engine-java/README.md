# Prior Auth Copilot engine (Java)

A Spring Boot port of the Prior Auth Copilot engine in the site project's `src/lib/pa/engine.ts`. It runs the same fixed workflow and returns the same JSON, so the Next.js demo can use either engine. It's part of this multi-module project. See [../README.md](../README.md) for the build, run commands and shared settings.

1. **Policy lookup** by service code
2. **Identifier redaction** (shared `Redactor`) before any text reaches the model
3. **Criteria evaluation** with Claude Opus 5.5 (`ClaudeEvaluator`). It uses structured output derived from the `ModelOutput` record, through the shared `ClaudeSupport`.
4. **Citation check** in code. A "met" or "not met" with no quote found word for word in the record is downgraded to "insufficient".
5. **Routing** in code (`PaEngine.finalizeResult`): all met → recommend approval; anything missing → request info; anything not met → nurse review. It never recommends a denial.

Cases, policies and recorded outputs come from `../data/pa/*.json`.

## Run

After `mvn install` in the project root, run this from this folder:

```bash
mvn spring-boot:run
```

| Endpoint (port 8081) | Purpose |
|---|---|
| `GET /api/pa/evaluate` | `{ liveAvailable, engine }` |
| `POST /api/pa/evaluate` | Body `{ "caseId": "PA-2026-01487", "mode": "replay" \| "live" }` |
| `GET /api/pa/cases` | The demo cases |
| `GET /actuator/health` | Health check |

To use it from the demo, set `PA_ENGINE_URL=http://localhost:8081` in the site project's `.env.local`.

## Tests

`PaEngineTest` covers:
- the citation downgrade
- routing to nurse review, and never to a denial
- missing and invalid statuses
- a replay of all three cases, checked against `evals/labels.json`, including the CMS-0057-F decision deadlines

## Toward production

- **Claude access:** call Claude through Amazon Bedrock in the plan's AWS account.
- **Data:** replace `PaData` with intake from the UM platform (X12 278 or FHIR Da Vinci PAS) and a versioned policy library.
- **Throughput:** move evaluation onto a queue worker, because each live call takes 10–60 seconds.
- **Audit:** add an audit log of the input, output, policy version and reviewer action for every case.
