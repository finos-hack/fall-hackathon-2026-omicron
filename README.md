# Omicron — AML Alert Triage Steel Thread

**Hackathon Track:** 2 (Governance-as-Code)

**Team:** Saketh Cherukuri ([@ssaketh-ch](https://github.com/ssaketh-ch)) · Sai Sravan Cherukuri ([@saisravan909](https://github.com/saisravan909))

Applies the FINOS AI Steel Thread governance pipeline to a new use case: **AML alert triage**, with a deterministic policy gate that bounds agent non-determinism. Based on the FINOS AI Steel Thread demo (see [UPSTREAM.md](./UPSTREAM.md)).

---

## What this fork adds (Track 2)

- **New use case:** AML alert triage (FINOS AIGF [UC-4](https://github.com/finos/ai-governance-framework/blob/main/docs/_usecases/uc-4_anti-financial-crime-investigation.md)), process `AfcInvestigation_AI`.
- **Deterministic policy gate:** a pure rule engine scores each alert; the agent's recommendation is advisory only. Policy AFC-3.2: score ≥ 70 → mandatory human review; ≥ 40 → review with advisory; below → an investigator still records the disposition.
- **Tool-using triage agent:** the model may call three read-only investigation tools (customer profile, sanctions screening, transaction statistics). Tool calls are audited in the evidence pack (CCC.GenAI.CN06, least privilege).
- **Acceptance-as-code:** `GET /api/afc/acceptance-tests` checks that every score band requires human review, agent output cannot close an alert, and the rule engine returns the same result across 100 runs.
- **Four FINOS projects in one pipeline:** Fluxnova (orchestration), CALM (architecture + controls), AIGF (mi-1/4/5/11), CCC (GenAI control catalog). Crosswalk: [docs/ccc-crosswalk.md](./docs/ccc-crosswalk.md).
- **Evidence pack:** per-instance PDF with rule hits, agent rationale, gate decision, policy citation, reviewer, and control coverage.
- **Runs fully local:** any OpenAI-compatible endpoint works; no paid API keys required.

## Quickstart (local model, no Docker)

```bash
# 1. Plugin snapshot (see Dockerfile for the authoritative recipe)
git clone --branch llm-token-usage https://github.com/finos/fluxnova-plugins.git
cd fluxnova-plugins && mvn -B -N -DskipTests install
cd agentic-subprocess && mvn -B -DskipTests install && cd ..

# 2. App
mvn -B -DskipTests package

# 3. Local model (any OpenAI-compatible server; example uses llama.cpp)
llama-server -m ~/models/Qwen3-8B-Q4_K_M.gguf --host 127.0.0.1 --port 8081 -c 16384 --jinja &

java -jar target/ai-steel-thread-demo-1.0-SNAPSHOT.jar \
  --spring.ai.openai.base-url=http://127.0.0.1:8081 \
  --spring.ai.openai.api-key=local \
  --spring.ai.openai.chat.options.model=qwen3

# 4. UI
cd frontend && npm ci && npm run dev   # http://localhost:5173
```

Open the UI, use the **AML Alert Triage** panel, and pick a preset (low / medium / high risk).

## Demo script (5 minutes)

1. **Problem (30 s):** AML teams drown in false positives; SAR deadlines and four-eyes review are non-negotiable.
2. **Architecture (60 s):** CALM model, four FINOS projects, deterministic gate outside the agent.
3. **Live run (2 min):** high risk → mandatory review; medium risk → agent says close, policy still requires review; low risk → agent says close, investigator still records the disposition.
4. **Human review + evidence (60 s):** complete the compliance task, download the evidence PDF, show control coverage (mi-1/4/5/11, CCC CN01/02/07/08).
5. **Close (30 s):** same pipeline as loan approval, new regulated use case, agent cannot reduce oversight.

## Verified locally (2026-11-13)

- `mvn -B -Dtest='AfcRuleEngineTest,AfcTriageParsingTest' test` → 13 tests pass (8 policy invariants, 5 parse-fallback cases).
- `GET /api/afc/acceptance-tests` → 5/5 deterministic checks pass.
- Live runs: score 85 → MANDATORY_REVIEW; score 50 with agent "close" → REVIEW_WITH_ADVISORY; score 0 with agent "close" → HUMAN_REVIEW_REQUIRED.
- Evidence PDF includes rule hits, agent tools used, policy citation, reviewer, and control coverage.
- Tokenomics records each triage call (example: 1 call, 351 tokens, $0 local inference).
- CALM validation: `npx @finos/calm-cli validate -a architecture/afc-alert-triage.calm.json -u architecture/url-to-local-file-mapping.json` → 0 errors, 0 warnings.
- Fresh clone of `main`: `mvn -DskipTests package`, 13 tests, and `npm ci && npm run build` all pass.

## Rubric mapping

| Category | Evidence |
|---|---|
| Ecosystem integration | Fluxnova BPMN + CALM + AIGF controls + CCC GenAI catalog, wired in one runtime and documented in `docs/ccc-crosswalk.md` |
| Technical execution | Working POC, unit-tested invariants (`AfcRuleEngineTest`, `AfcTriageParsingTest`), live acceptance suite at `/api/afc/acceptance-tests`, live UI, evidence PDF |
| Business value | AML alert triage, BSA/FinCEN context, four-eyes human review, audit-ready evidence |
| Bonus (AI build velocity) | Built agent-first; local LLM inference; acceptance-as-code invariants |

See [submit.md](./submit.md) for the full submission note.

---

## Requirements

- Java 21+
- Maven 3.9+
- Node.js 20+ (for the launch UI)
- Locally installed agentic plugins from [finos/fluxnova-plugins](https://github.com/finos/fluxnova-plugins) (`1.0.0-SNAPSHOT`) — or use Docker (builds the `llm-token-usage` branch automatically)
- `OPENAI_API_KEY` for OpenAI document intelligence / agent LLM calls
- `ANTHROPIC_API_KEY` for Anthropic (Claude) provider calls

## Docker (full stack)

Builds the app against `fluxnova-plugins` branch `llm-token-usage` (override with `PLUGINS_REF`), then runs API + Launch UI + Prometheus/Grafana/Tempo + CALM Hub.

```bash
cp .env.example .env
# edit .env — set OPENAI_API_KEY and ANTHROPIC_API_KEY

docker compose up --build
```

| Service | URL |
|---------|-----|
| Launch UI | http://localhost:5173 |
| API / Cockpit | http://localhost:8080 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| CALM Hub | http://localhost:8090 |

First build is slow (clones plugins + Maven download). Rebuild app only after code changes:

```bash
docker compose up --build app frontend
```

Pin plugins to a commit instead of the branch tip:

```bash
PLUGINS_REF=<commit-sha> docker compose build app
```

Observability-only (app running on the host) remains available via `observability/docker-compose.yml`.

## Build

```bash
# Install plugins locally first (from fluxnova-plugins / llm-token-usage):
#   cd ../fluxnova-plugins/agentic-subprocess && mvn -DskipTests install

mvn clean package
```

## Run

```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
mvn spring-boot:run
```

`LoanApprovalProcess_AI` and the Fluxnova form `credit_approval_review` are auto-deployed from
`src/main/resources/bpmn/` and `src/main/resources/forms/` on startup
(`fluxnova.bpm.auto-deployment-enabled` + `deployment-resource-pattern` includes `*.form`).
You can still redeploy from Fluxnova Modeler when iterating on the diagram or form.

### Launch UI (React)

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — choose **Loan Approval**, fill the form, then **Send**.

The Vite dev server proxies `/api` to `http://localhost:8080`.

### API (optional)

```bash
curl -X POST 'http://localhost:8080/api/demo/loan-approval/start' \
  -H 'Content-Type: application/json' \
  -d '{"requestedAmount":25000,"loanPurpose":"Car"}'
```

Legacy query-param endpoint: `POST /api/demo/loan-approval/start-with-sample-id`

## Architecture (FINOS CALM)

Machine-readable architecture for this loan-approval steel thread:

[`architecture/loan-approval.calm.json`](architecture/loan-approval.calm.json)

From the Launch UI use-case card, click **View CALM architecture** for an in-app React Flow canvas (same CALM nodes / relationships model as [CALM Hub](https://calm.finos.org/calm-hub/)). Optionally run Hub on port 8090 and open the official visualizer — see [`architecture/README.md`](architecture/README.md).

```bash
npx @finos/calm-cli validate \
  -a architecture/loan-approval.calm.json \
  -u architecture/url-to-local-file-mapping.json \
  -f pretty
```

## Observability

See `observability/README.md` for Prometheus / Grafana / Tempo via Docker Compose.
