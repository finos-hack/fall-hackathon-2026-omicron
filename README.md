# Omicron AML Alert Triage

**FINOS Fall Hackathon 2026, Track 2**

Omicron is a synthetic anti money laundering alert triage demonstration. It connects a deterministic policy check, a FINOS CALM architecture, and a FINOS Fluxnova BPMN process. Every alert reaches an investigator task. The software assigns a queue priority only. A person records the case disposition.

## Demonstration boundaries

All cases and thresholds are invented for this demonstration. The screening match is a synthetic input, not a match against a real sanctions or watch list. The optional advisory in one fixture is static text. No model is called. The software does not decide that activity is suspicious, determine whether a report is required, or file a report. A deposit pattern alone does not establish intent under 31 U.S.C. 5324.

The FINOS AI Governance Framework anti financial crime investigation use case is marked draft. Omicron follows its human accountability boundary: an AI or rule output can inform an investigator, while an authorized person retains responsibility for disposition. The demonstration does not make a legal or compliance determination.

## Run the checks

Requirements: Python 3.9 or newer, Node.js 22.13 or newer, Java 21, and Maven 3.9 or newer.

```sh
bash scripts/demo.sh
```

The command checks that the DMN thresholds match the versioned synthetic policy, runs the Python policy and workflow checks, exercises low, medium, and high sample alerts, validates the architecture with the official FINOS CALM CLI, and runs the Java integration tests against Fluxnova.

The Java integration tests confirm that high and low priority cases both stop at the investigator task. A low queue priority never closes an alert automatically.

## Run the Fluxnova service

From the repository root:

```sh
mvn spring-boot:run
```

The local service uses an in memory H2 database and has authentication disabled for this synthetic demonstration. Do not expose it to a network or use real customer data.

Check readiness:

```sh
curl http://127.0.0.1:8080/api/health
```

Start a synthetic case:

```sh
curl -X POST http://127.0.0.1:8080/api/triage \
  -H 'Content-Type: application/json' \
  -d '{"caseId":"SYNTHETIC-001","depositCount24h":4,"depositTotal24hUsd":20000,"screeningCandidateMatch":false,"inputSha256":"e41b2fc8d99a"}'
```

The response contains the queue priority and investigator task identifier. Record a human disposition by replacing `TASK_ID` with the returned task identifier:

```sh
curl -X POST http://127.0.0.1:8080/api/triage/tasks/TASK_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"investigator-1","disposition":"escalate"}'
```

Supported human dispositions are `close_as_false_positive`, `escalate`, and `request_information`. Report filing is outside the demonstration.

## FINOS integration and rubric map

| Hackathon criterion | Demonstration |
| --- | --- |
| FINOS ecosystem integration | FINOS Fluxnova runs the BPMN process and DMN decision. The architecture uses FINOS CALM and maps related controls from Common Cloud Controls. The governance boundary references AIGF use case 4. |
| Functional proof of concept | Python scenarios and Java integration tests show priority calculation, a waiting investigator task, and human disposition. |
| Financial services value | The workflow makes a synthetic alert queue more consistent while preserving investigator accountability. |
| Bounded non determinism | No model is called. A static conflicting advisory fixture is ignored by the deterministic policy. |
| Emerging technology | The sample combines CALM architecture as code with a BPMN and DMN runtime. |

The CALM architecture maps `CCC.Core.CN04` to access and change logging and `CCC.Core.CN09` to log integrity as partial relationships. These mappings identify areas for production evidence. They do not certify that this sample implements either control completely. The AIGF use case is a draft.

## Project layout

* `policy/aml_triage_policy.json` contains the synthetic policy and boundaries.
* `src/omicron/triage.py` evaluates the sample alerts and records human disposition events.
* `fluxnova/aml_alert_triage.bpmn` and `fluxnova/aml_triage_priority.dmn` define the orchestration and priority decision.
* `src/main/java/` exposes a small REST interface to the Fluxnova runtime.
* `calm/architecture/aml_alert_triage.architecture.json` describes the integrated system.
* `examples/alerts/` contains fictional sample cases.
* `tests/` checks the policy, process model, and control mappings.

## Sources

* [FINOS AI Governance Framework, anti financial crime investigation](https://github.com/finos/ai-governance-framework/blob/main/docs/_usecases/uc-4_anti-financial-crime-investigation.md)
* [FINOS Fluxnova](https://github.com/finos/fluxnova-bpm-platform)
* [FINOS CALM schema](https://github.com/finos/calm-schema)
* [FINOS Common Cloud Controls](https://github.com/finos/common-cloud-controls)
* [31 U.S.C. 5324, official United States Code](https://uscode.house.gov/us/usc/119-124/t31/s5324/)

## License

Copyright 2026 FINOS. Distributed under the Apache License, Version 2.0. See [LICENSE](./LICENSE).
