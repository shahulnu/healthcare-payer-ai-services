# Denial & Appeals Assistant engine (Java)

A Spring Boot port of the Denial & Appeals Assistant engine in the site project's `src/lib/appeals/engine.ts`. It runs the same workflow and returns the same JSON, so the Next.js demo can use either engine. It's part of this multi-module project. See [../README.md](../README.md) for the build, run commands and shared settings.

1. **Remittance parsing** in code: denied lines plus CARC/RARC meanings from the code book (`CodeBook`, `DeniedClaim.RemitLine.denialCodes()`)
2. **Identifier redaction** (shared `Redactor`)
3. **Denial analysis** with Claude: per denied line, a plain-English reason, category, suggested action and verbatim evidence. It uses structured output derived from `AnalysisOutput`.
4. **Evidence check** in code (`AppealsEngine.finalizeFindings`). A line is pursued only if verified quotes from the provider's own records answer the denial.
   - Strength is strong with 2 or more such quotes, and moderate with 1.
   - Unsupported appeals and corrected claims are downgraded to "don't pursue".
5. **Appeal letter** with Claude, drafted only for lines that passed step 4 and only from their verified quotes (`LetterOutput`)
6. **Letter check** in code (`AppealsEngine.checkLetter`): every quoted passage is matched against the records and policy

Corrected-claim instructions (replacement claim, frequency code 7, original claim number) are assembled in code. Prompts and message builders are in `AppealsPrompts`, word for word the same as the TypeScript engine's.

Claims, policies, codes and recorded outputs come from `../data/appeals/*.json`.

## Run

After `mvn install` in the project root, run this from this folder:

```bash
mvn spring-boot:run
```

| Endpoint (port 8082) | Purpose |
|---|---|
| `GET /api/appeals/analyze` | `{ liveAvailable, engine }` |
| `POST /api/appeals/analyze` | Body `{ "claimId": "CLM-88310427", "mode": "replay" \| "live" }` |
| `GET /api/appeals/claims` | The demo claims |
| `GET /actuator/health` | Health check |

To use it from the demo, set `APPEALS_ENGINE_URL=http://localhost:8082` in the site project's `.env.local`. A live run makes two Claude calls when a letter is needed, about 30 seconds and 5–8 cents.

## Tests

`AppealsEngineTest` covers:
- only denied lines are analyzed, and CARC 45 write-offs aren't treated as denials
- strength comes from verified quotes in the provider's records, not the policy
- appeals backed only by policy text or paraphrases are downgraded
- corrected-claim rules
- unknown or missing model output
- the letter quote check
- a replay of all three claims, checked against `evals/appeals-labels.json`

## Toward production

- **Data:** read 835 remittances and 277CA acknowledgments from the clearinghouse feed, and records from the EHR document store.
- **Code tables:** load the full CARC/RARC tables and update them with each X12 release.
- **Submission:** send corrected claims as 837 replacements, and appeals through the payer portal or API.
- **Learning loop:** record appeal outcomes and train an overturn model, in Python, to replace the rule-based strength score. Run its predictions in Java with XGBoost4J or ONNX.
