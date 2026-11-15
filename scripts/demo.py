#!/usr/bin/env python3
"""Run the synthetic scenarios and write a hashed human review event."""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from omicron.triage import evaluate_alert, record_human_disposition  # noqa: E402


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    policy = load(ROOT / "policy/aml_triage_policy.json")
    scenarios = [
        ROOT / "examples/alerts/low.json",
        ROOT / "examples/alerts/medium.json",
        ROOT / "examples/alerts/high_with_conflicting_advisory.json",
    ]
    results = []
    print("Omicron synthetic AML alert triage")
    print("Thresholds and sample activity are invented for this demo.")
    for path in scenarios:
        alert = load(path)
        result = evaluate_alert(alert, policy, advisory=alert.get("advisory"))
        results.append(result)
        print(
            f"{result['case_id']}: {result['priority']} priority, "
            f"{result['status']}, score {result['score']}, "
            f"human review required"
        )
        if result["advisory"]["present"]:
            print("  Conflicting advisory was ignored for score and disposition; raw text was not saved.")
    high = results[-1]
    review = record_human_disposition(
        high,
        reviewer_id="demo-investigator-001",
        disposition="escalate",
    )
    artifacts = ROOT / "artifacts"
    artifacts.mkdir(exist_ok=True)
    (artifacts / "human_decision_record.json").write_text(
        json.dumps(review, indent=2) + "\n", encoding="utf-8"
    )
    print(
        f"Human decision recorded by {review['reviewer_id']}: {review['disposition']} "
        f"(event SHA 256 {review['event_sha256'][:16]}...)"
    )
    print("No alert is closed and no report is filed by the assistant.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
