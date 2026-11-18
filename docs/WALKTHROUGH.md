# Walkthrough: what am I looking at?

This is a one-page orientation for someone opening the repo or the demo cold.
Read this first; everything else is detail.

## The 30-second version

We took FINOS's AI Steel Thread demo (a governed loan-approval pipeline) and
used it to run a **second** regulated workflow: **AML alert triage**. The
point is a deterministic policy layer that the AI cannot override. The AI
gives an opinion; the rules decide; a human reviews when policy says so; a
PDF proves what happened.

## What to click (2 minutes)

1. Open http://localhost:5173
2. Scroll to the **AML Alert Triage (AIGF UC-4)** panel.
3. Note the green line: **Acceptance (mi-5): 5/5 deterministic checks pass**.
4. Click **High risk** preset, then **Start alert triage**.
5. Watch three numbers appear:
   - **Deterministic score: 85** (rules only, no AI)
   - **Agent opinion: escalate** (the model, advisory only)
   - **Policy decision: Mandatory human review** (the gate)
5. Do the same with **Medium risk**. The agent says **close**, but the policy
   still says **human review**. That is the whole idea in one screen.
6. Click **Complete review**, then **Download evidence pack (PDF)**.

## What the numbers mean

| Number | Who produces it | Can it be argued with? |
|---|---|---|
| Rule score (0-100) | Pure Java rule engine: structuring, high-risk country, velocity, sanctions near-match, PEP, round amounts | No. Same alert, same score, every time. |
| Agent opinion (close/escalate) | The local LLM, on a redacted copy of the alert | No. It is recorded as evidence and never changes routing. |
| Policy decision | A fixed matrix: score 70+ = mandatory review; 40-69 = review with advice; below = investigator disposition still required | No. This is the control. |
| Tool calls | The agent may call three read-only tools (customer profile, sanctions, transaction stats) | Yes, but it cannot change anything; every call is logged |
| Acceptance | Five deterministic invariants checked live at `/api/afc/acceptance-tests` | No. Green means the guarantees hold. |

## Where things live

| What | Where |
|---|---|
| The workflow (what runs in what order) | `src/main/resources/bpmn/AfcInvestigation_AI.bpmn` |
| The scoring rules and policy matrix | `src/main/java/org/finos/ai/demo/afc/AfcRuleEngine.java`, `AfcPolicyMatrix.java` |
| The AI call (redaction happens here) | `AfcLlmTriageService.java` |
| HTTP endpoints | `AfcDemoController.java` |
| Evidence PDF | `AfcEvidencePdfRenderer.java` |
| Architecture-as-code with controls | `architecture/afc-alert-triage.calm.json` |
| Which FINOS control lives where | `docs/ccc-crosswalk.md` |
| Judge-facing summary | `submit.md` |
| UI panel | `frontend/src/AfcPanel.tsx` |

Upstream (not ours): the Fluxnova engine, Spring Boot plumbing, agent plugin,
loan-approval flow, observability, tokenomics, and the rest of the UI. Ours:
everything under `org/finos/ai/demo/afc`, the new BPMN process, the CALM file,
the crosswalk doc, and the UI panel.

## How to run it

See the Quickstart in `README.md`. Short version: app on 8080, UI on 5173,
local model on 8081. Run one alert, then open Tokenomics; it shows tokens per
triage call.

## What to say in one sentence

"We reused the FINOS steel thread pipeline for a new regulated use case, and
proved the AI cannot bypass the deterministic controls or the human review."
