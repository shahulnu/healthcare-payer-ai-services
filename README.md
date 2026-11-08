# Healthcare Payer AI Services

Spring Boot 3.5 / Java 21 services for five AI applications in U.S. health plan workflows, built as one multi-module Maven project. All data is synthetic.

They're the Java engines behind the [Healthcare Payer AI Portfolio](https://github.com/shahulnu) site, which is a separate Next.js project. Each service returns the same JSON as the site's TypeScript engine, so the site can switch to Java with one setting.

| Module | What it is | Port |
|---|---|---|
| `payer-ai-common` | Shared library (see below) | — |
| `pa-engine-java` | Prior Auth Copilot | 8081 |
| `appeals-engine-java` | Denial & Appeals Assistant | 8082 |
| `benefits-engine-java` | Benefits Navigator (tool-use agent) | 8083 |
| `pi-engine-java` | Payment Integrity Radar (peer statistics and checked briefs) | 8084 |
| `hcc-engine-java` | HCC Evidence Finder (MEAT review and code-owned reconciliation) | 8085 |

**What `payer-ai-common` holds:**
- `Redactor` and `Citations`: identifier redaction and word-for-word quote checks
- `ClaudeSupport`: the official Anthropic Java SDK client.
  - `baseParams()` sets up the model, a cached system prompt and server-side fallback for any call, including the benefits agent's tool loop.
  - `structured()` makes a structured-output call on top of that and works out token cost.
- `LiveMode` and `LiveLimiter`: replay vs. live decisions and the per-visitor daily cap
- `ApiExceptionHandler`: one `@RestControllerAdvice` that maps model and SDK errors to clear responses
- `Trace`, `TraceStep`, `TokenUsage`, `RedactionCount`: the run-trace and cost types every result carries
- `ContractCheck`: compares an engine's output with a golden file (see Contract tests below)

Every service scans `com.ummarali.payerai`, so it picks up the shared beans automatically.

## Requirements

- JDK 21, with `JAVA_HOME` set
- Apache Maven 3.9+

## Build and test

From this folder:

```bash
mvn install
```

This builds the six modules in order and runs their 112 unit and contract tests. It also installs `payer-ai-common` in your local Maven repository, so each service can then start on its own. No API key is needed, and no API calls are made.

## Run

Run each service from its own folder, for example:

```bash
cd pa-engine-java
mvn spring-boot:run
```

Each service exposes `GET /actuator/health`.

**API key:** each service reads `ANTHROPIC_API_KEY` from the environment. If it isn't set, it reads the key from `.env` in this folder when started from a module's folder; copy `.env.example` to create it. Without a key, only replay works. In production, set the environment variable.

## Use them from the demo site

In the site project's `.env.local`:

```
PA_ENGINE_URL=http://localhost:8081
APPEALS_ENGINE_URL=http://localhost:8082
BENEFITS_ENGINE_URL=http://localhost:8083
PI_ENGINE_URL=http://localhost:8084
HCC_ENGINE_URL=http://localhost:8085
```

The site's dev server picks up the change within a few seconds. Each demo's run trace then shows **"Java engine"**. Set any of them. Clear a line to switch that demo back to TypeScript.

## Data and contract tests

Two folders here are **copies** from the site project. Don't edit them by hand:

| Folder | What it is |
|---|---|
| `data/` | Cases, claims, plans, providers, charts, policies, codes and recorded outputs. Maven copies it onto the classpath at build time. |
| `contract-tests/` | 25 golden JSON files, one per demo case, generated from the TypeScript engines in replay mode |

Each service has a `ContractTest` that replays every case and compares the output with its golden file. If the Java and TypeScript engines drift apart, `mvn test` fails and names the exact field. These tests have already caught one real difference, in how the Prior Auth decision deadline was formatted.

Numbers compare by value and a missing field equals null. Fields that vary between runs (`generatedAt`, `usage`, `notice`, `engine`, step timings) are left out.

**To update the contract**, after changing data or recorded outputs in the site project, run this there with its dev server running:

```bash
npm run contracts:sync
```

Then run `mvn test` here. If the change is intentional and a test fails, the Java engine needs the same change.

## Settings (`application.properties` in each service)

| Property | Default | Meaning |
|---|---|---|
| `payerai.model` | `claude-opus-5-5` | Model for live runs |
| `payerai.daily-limit` | `10` | Live runs per client IP per day |
| `payerai.env-file` | `../.env` | Where to look for the API key if the environment has none, relative to where the service starts |
| `server.port` | `8081` to `8085` | HTTP port |

## Deploying

Each service builds its own runnable jar:

```bash
java -jar pa-engine-java/target/pa-engine-java-0.1.0.jar
```

Set `ANTHROPIC_API_KEY` in the environment for live mode. Each service needs its own JVM, roughly 300 MB or more, so five services cost real money to host. If you only need them for interviews, run them locally.

Comments in the code that mention paths such as `src/lib/pa/engine.ts` refer to the matching TypeScript code in the site project.

Service details: [pa-engine-java](pa-engine-java/README.md), [appeals-engine-java](appeals-engine-java/README.md), [benefits-engine-java](benefits-engine-java/README.md), [pi-engine-java](pi-engine-java/README.md), [hcc-engine-java](hcc-engine-java/README.md).
