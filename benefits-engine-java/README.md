# Benefits Navigator engine (Java)

A Spring Boot port of the Benefits Navigator in the site project's `src/lib/benefits/`. It runs the same tool-using agent and the same answer checks, and returns the same JSON, so the Next.js demo can use either engine. It's part of this multi-module project. See [../README.md](../README.md) for the build, run commands and shared settings.

## How it works

`BenefitsAgent` runs a manual tool-use loop with the Anthropic Java SDK, up to 6 model turns. The three tools have strict JSON schemas (`strict(true)`, `additionalProperties: false`):

| Tool | Implemented by | What it does |
|---|---|---|
| `get_plan_sections` | `PlanTools` | Returns sections of the member's SBC and EOC by id. The model gets a table of contents. |
| `estimate_cost` | `CostCalculator` | Applies the remaining deductible, copay or coinsurance and the out-of-pocket cap to the allowed amount, and adds prior authorization, referral, visit-limit and balance-billing notes. The model never does the math. |
| `submit_answer` | the model | The answer, verbatim citations, and whether a person should take over |

Each assistant turn is appended unchanged with `Message.toParam()`, and all of a turn's tool results go back in one user message.

`AnswerChecker` then applies the same code checks as the TypeScript engine:
- every quote must appear word for word in its plan section
- every dollar amount must come from a section the model read or a calculator result
- out-of-scope questions, unsupported figures and unverified citations are handed to an agent

`BenefitsEngine` ties it together. In replay mode it re-runs the recorded tool calls against `CostCalculator`, so estimates always reflect the current data.

Plans, members, services and recorded runs come from `../data/benefits/*.json`.

## Run

After `mvn install` in the project root, run this from this folder:

```bash
mvn spring-boot:run
```

| Endpoint (port 8083) | Purpose |
|---|---|
| `GET /api/benefits/ask` | `{ liveAvailable, engine }` |
| `POST /api/benefits/ask` | Body `{ "questionId": "Q-PPO-MRI", "mode": "replay" \| "live" }`, or `{ "memberId": "M-58817", "question": "...", "mode": "live" }` for a typed question |
| `GET /api/benefits/members` | The demo members and their sample questions |
| `GET /actuator/health` | Health check |

To use it from the demo, set `BENEFITS_ENGINE_URL=http://localhost:8083` in the site project's `.env.local`. A live question takes 2–4 model turns, about 15–25 seconds and 4 cents.

## Tests

`BenefitsEngineTest` (24 tests) covers:
- the calculator, on the same 9 scenarios checked against the TypeScript calculator, plus its notes
- dollar-amount parsing
- the number check, citation check and model-requested hand-offs
- strict tool schemas
- a replay of all 9 sample questions, checked against `evals/benefits-labels.json`

## Toward production

- **Data:** load plan documents from the product library and index them for hybrid search, instead of a table of contents.
- **Accumulators:** read accumulators live from the claims system.
- **Prices:** take allowed amounts from the plan's price-transparency data.
- **New tool:** add a provider-network lookup.
- **Hand-off:** pass the conversation and citations to the live agent's desktop.
