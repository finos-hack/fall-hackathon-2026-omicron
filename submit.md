# Omicron AML Alert Triage

* **Hackathon:** FINOS Fall Hackathon 2026
* **Track:** Track 2, ecosystem integration
**Repository:** https://github.com/finos-hack/fall-hackathon-2026-omicron

## Summary

Omicron demonstrates synthetic anti money laundering alert triage across FINOS Fluxnova, CALM, and Common Cloud Controls. A deterministic DMN decision assigns an investigation queue priority. Every case stops at an investigator task, where a person records the disposition. The system never closes an alert or files a report on its own.

## Demonstration

Run `bash scripts/demo.sh` from the repository root. The script checks the Python policy and BPMN model, runs three synthetic alert scenarios, validates the CALM architecture with the official FINOS CLI, and runs the Fluxnova Java integration tests.

The service also exposes a local REST interface. See the run instructions and sample requests in [README.md](./README.md).

## Rubric alignment

* **FINOS integration:** Fluxnova BPMN and DMN execution, a CALM architecture, related Common Cloud Controls mappings, and governance boundaries informed by the AIGF anti financial crime investigation use case.
* **Functional proof of concept:** Repeatable sample scenarios and integration checks demonstrate priority calculation and the investigator gate.
* **Financial services value:** Consistent queue prioritization with a human accountable for each disposition.
* **Bounded AI behavior:** The demo has no model call. A conflicting static advisory cannot affect the deterministic score or close a case.
* **Emerging technology:** CALM architecture as code and a BPMN runtime are combined in a runnable example.

## Scope

Every name, transaction, amount, match, and threshold is synthetic. The sample does not perform customer screening, classify actual activity as suspicious, determine a filing obligation, or submit a report. Its priority is not a legal conclusion. The AIGF use case referenced here is marked draft upstream.
