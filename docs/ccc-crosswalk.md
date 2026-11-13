# FINOS project crosswalk: AIGF x CCC (AML alert triage)

This submission runs four FINOS projects in one pipeline. The table below shows
where each control is enforced at runtime, so judges and auditors can trace the
claim to code.

| FINOS project | Artifact | Runtime enforcement |
|---|---|---|
| **Fluxnova BPM** | `src/main/resources/bpmn/AfcInvestigation_AI.bpmn` | Orchestrates pre-screen, LLM triage, policy gate, human review, finalize |
| **CALM** | `architecture/afc-alert-triage.calm.json` | Architecture-as-code with controls attached to the enforcing nodes |
| **AIGF** | `architecture/controls/mi-*.requirement.json` | mi-1 redaction at LLM egress, mi-4 token/audit variables, mi-5 acceptance invariants, mi-11 human review task |
| **CCC (GenAI catalog)** | `docs/ccc-crosswalk.md` (this file) | GenAI controls mapped onto the same nodes and cited in the evidence PDF |

## CCC.GenAI controls used

Source: `finos/common-cloud-controls`, `catalogs/ai-ml/gen-ai/controls.yaml`
(the catalog already maps to AIGF threats upstream via
`mappings/threats-finos-aigf.yaml`).

| CCC control | Title | Where it is enforced |
|---|---|---|
| `CCC.GenAI.CN01` | Model Input Filtering and Sanitisation | `AfcLlmTriageService.redact(...)` strips long identifiers before the alert reaches the model |
| `CCC.GenAI.CN02` | Model Output Filtering and Sanitisation | Strict JSON parse, lenient field extraction, then safe default to `escalate` |
| `CCC.GenAI.CN07` | Model Version Pinning | Endpoint and model are fixed by deployment config and recorded per instance |
| `CCC.GenAI.CN08` | Quality Control and Red Teaming | Deterministic policy invariants in `AfcRuleEngineTest`; auto-closed alerts are sampled for QA |

## AIGF controls used

| AIGF mitigation | Title | Where it is enforced |
|---|---|---|
| `mi-1` | AI data leakage prevention | Egress redaction in `AfcLlmTriageService`; redaction count in the evidence pack |
| `mi-4` | AI system observability | Prompt/completion token counts and gate rationale recorded per instance |
| `mi-5` | System acceptance testing | `AfcRuleEngineTest`: agent can never downgrade oversight; high-risk always escalates |
| `mi-11` | Human feedback loop | `UserTask_AfcComplianceReview` with decision, rationale, reviewer |

## Why this matters for Track 2

Track 2 asks for governance and controls applied to a **new use case** beyond
loan approval, with **multiple FINOS projects working synergistically**. The
loan approval demo proves the pattern; this submission proves it generalises:
same engine, same control catalogues, new regulated workflow (AML alert
triage, AIGF UC-4), plus a deterministic gate the agent cannot bypass.
