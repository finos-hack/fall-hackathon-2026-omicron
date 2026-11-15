#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p artifacts

printf '%s\n' '1. Check that the Fluxnova DMN matches the versioned policy'
python3 scripts/check_dmn_policy.py

printf '\n%s\n' '2. Run deterministic policy and process model checks'
PYTHONPATH="$ROOT/src" python3 -m unittest discover -s tests -p 'test_*.py' -v

printf '\n%s\n' '3. Run the synthetic alert scenarios'
PYTHONPATH="$ROOT/src" python3 scripts/demo.py

printf '\n%s\n' '4. Validate the CALM architecture with the official FINOS CLI'
npx --yes @finos/calm-cli@1.72.0 validate \
  --architecture calm/architecture/aml_alert_triage.architecture.json \
  --format pretty

if command -v mvn >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  printf '\n%s\n' '5. Execute the Fluxnova BPMN and DMN integration tests'
  mvn -B verify
else
  printf '\n%s\n' 'Fluxnova runtime verification needs JDK 21 and Maven. CI runs mvn verify.'
fi
