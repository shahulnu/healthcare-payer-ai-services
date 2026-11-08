# Healthcare Payer AI Services (Spring Boot)

The Java versions of the five demo engines in the portfolio site (`../healthcare-payer-ai-portfolio`). Owner: **Shahul Ummarali**, a software architect whose U.S. healthcare payer client needs Java. Each service returns **exactly the same JSON** as its TypeScript twin, so the site can switch to it by setting an `*_ENGINE_URL` variable.

## Stack
- Spring Boot 3.5.6, Java 21, Maven multi-module (root `pom.xml`).
- `com.anthropic:anthropic-java:2.34.0`. Model `claude-opus-5-5`. Patterns: `StructuredMessageCreateParams` with `.outputConfig(SomeRecord.class)`; server-side fallback via `putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")` + `putAdditionalBodyProperty("fallbacks", "default")`; `Message.toParam()` and `Tool.builder().strict(true)` for the Benefits tool loop. Shared helpers are in `ClaudeSupport`.
- Package root `com.ummarali.payerai`. Each app uses `@SpringBootApplication(scanBasePackages = "com.ummarali.payerai")` so it picks up the shared code.

## Modules
| Module | Port | Package | Endpoints |
|---|---|---|---|
| `payer-ai-common` | (library) | `common` | `ClaudeSupport`, `LiveMode`, `LiveLimiter`, `Citations`, `RedactionCount`, `ApiExceptionHandler` (`@RestControllerAdvice`), `ContractCheck` |
| `pa-engine-java` | 8081 | `pa` | `/api/pa/evaluate` |
| `appeals-engine-java` | 8082 | `appeals` | `/api/appeals/analyze` |
| `benefits-engine-java` | 8083 | `benefits` | `/api/benefits/ask` |
| `pi-engine-java` | 8084 | `pi` | `/api/pi/leads`, `/api/pi/brief` |
| `hcc-engine-java` | 8085 | `hcc` | `/api/hcc/review` |

Config: `application.properties` sets `payerai.model`, `payerai.daily-limit` (keep at 10) and `payerai.env-file=../.env`. That relative path resolves correctly when a service is started from its module folder.

## Rules (same as the site)
- **Code makes the decision; the model reads and explains.** Quotes are checked word for word, and failed checks hold the output. If a check fails, fix the prompt, not the check.
- Replay (recorded runs) is the default; Live needs the key and is capped per IP per day.
- All data is synthetic. No PHI.

## Data and contract tests: copies, never edit here
- `data/` and `contract-tests/` are **copied from the site** by `npm run contracts:sync` (run in the site folder with its dev server running). The site's `data/` is the master copy. Edits made here are overwritten on the next sync.
- Each service has a `ContractTest` that replays every golden case and compares the result field by field with `ContractCheck` (engine, timestamps, notice, usage and trace timings are ignored). This has caught real drift before: Java printed `decisionDueBy` without milliseconds, fixed with the `ISO_MILLIS` formatter in `PaEngine`.
- Contract tests cover replay only, not live model output.
- Any change to an output shape must be made in both languages, then synced and tested.

## Build and run
```
mvn clean install                          # from this folder; 112 tests at last count
cd pa-engine-java && mvn spring-boot:run   # likewise for the other modules
```
To use them from the site, set e.g. `PA_ENGINE_URL=http://localhost:8081` in the site's `.env.local` and restart its dev server. Set the URLs back to empty afterward.

## Standing rules from the owner
- **Don't commit or push to git until asked.** This will be its own GitHub repo, separate from the site.
- Never print or echo the API key. It's in `.env` here (git-ignored) and in the site's `.env.local`; a rotated key must be updated in both. `.env.example` stays blank.
- Stop all Java services when done.
