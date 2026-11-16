# Demo script

Two paths: a 5-minute version for judging and a 15-minute version for
office hours or deeper Q&A. Both assume the app and UI are running (see
README Quickstart) and the model endpoint is up.

## Pre-flight (do this 5 minutes before)

1. `curl -s localhost:8080/actuator/health` returns UP.
2. `curl -s localhost:8081/v1/models` returns a model.
3. Open http://localhost:5173 and scroll to the AML Alert Triage panel.
4. Run one **Low risk** alert and leave it completed (so Tokenomics has data).
5. Have one PDF viewer tab ready.

## 5-minute path

**0:00-0:30 Problem.**
"AML teams review millions of alerts, most are false positives, and the work
sits under SAR deadlines and four-eyes rules. Agents can help, but in a bank
an agent's opinion cannot decide on its own."

**0:30-1:30 Architecture.**
Point at the three cards in the panel: rules score, model advises, policy
decides. Mention the four FINOS projects: Fluxnova runs the process, CALM
describes the system and controls, AIGF supplies the mitigations, CCC supplies
the GenAI cloud controls.

**1:30-3:30 Live run.**
1. Point at the green acceptance line: 5 of 5 deterministic checks pass.
2. Click **High risk**, **Start alert triage**.
   - Say: score 85, agent says escalate, and note the tools line: the agent
     called `sanctionsScreening` before answering. Policy says mandatory review.
2. Click **Medium risk**, **Start alert triage**.
   - Wait for the amber callout: "The agent wanted to close this alert.
     Policy still requires human review."
   - Say: that is the whole thesis. The agent cannot downgrade oversight.
3. Click **Complete review** on the medium alert (escalate, reviewer, rationale).
4. Click **Download evidence pack (PDF)** and open it.
   - Point at: rule hits, agent rationale, policy citation, reviewer, control
     coverage (mi-1/4/5/11 and CCC CN01/02/07/08).

**3:30-4:30 Why it is safe.**
"mi-1 redaction happens before the model sees the alert. Malformed model
output degrades to escalation, never to a close. The policy matrix is unit
tested: agent recommendations can only add oversight."

**4:30-5:00 Close.**
"Same steel thread as loan approval, new regulated use case, and the controls
are enforced in code, not slideware. Everything you saw is in the repo."

## 15-minute path (adds depth)

- Show `AfcRuleEngineTest` and explain the monotonicity test.
- Open `architecture/afc-alert-triage.calm.json` and point at controls on the
  LLM endpoint and rule engine nodes.
- Open `docs/ccc-crosswalk.md` and show the CCC control mapping.
- Open Tokenomics and show tokens per call at $0 local spend.
- Explain what is upstream vs ours (WALKTHROUGH.md has the map).

## Likely judge questions and short answers

- **Why not use the agent subprocess from the loan flow?**
  The agent plugin is tightly coupled to loan variables. The governance layer
  does not depend on which agent runtime calls the model; the loan flow still
  demonstrates the agentic pattern. Swapping the triage step to the ad-hoc
  subprocess is an integration task, not a redesign.
- **Is the model doing anything real?**
  Yes: it reads the redacted alert, calls read-only tools (customer profile,
  sanctions screening, transaction stats), and returns a recommendation with
  rationale. Its tokens are metered and its tool calls are audited; its output
  is simply not authoritative.
- **What is mocked?**
  The alerts are synthetic. Everything else runs: rules, model call, BPMN,
  gate, human task, evidence PDF.
- **Why is the loan card showing an invalid document?**
  The loan flow's first step reads an ID-card image; the local text-only model
  cannot do vision. The AML flow needs no vision. Point judges at the AML
  panel; this is documented in the README.
