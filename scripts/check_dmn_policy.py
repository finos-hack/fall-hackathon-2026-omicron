#!/usr/bin/env python3
"""Check that DMN thresholds and inputs match the policy JSON."""

from __future__ import annotations

import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"dmn": "https://www.omg.org/spec/DMN/20191111/MODEL/"}


def main() -> int:
    policy = json.loads((ROOT / "policy/aml_triage_policy.json").read_text(encoding="utf-8"))
    tree = ET.parse(ROOT / "src/main/resources/aml_triage_priority.dmn")
    expressions = [item.text for item in tree.findall(".//dmn:inputExpression/dmn:text", NS)]
    rules = tree.findall(".//dmn:rule", NS)
    cells = [[node.text for node in rule.findall("./dmn:inputEntry/dmn:text", NS)] for rule in rules]
    if expressions != ["screeningCandidateMatch", "depositCount24h", "depositTotal24hUsd"]:
        raise SystemExit(f"Unexpected DMN input fields: {expressions}")

    thresholds = {rule["id"]: rule["threshold"] for rule in policy["rules"] if "threshold" in rule}
    expected = {"DEPOSIT_COUNT_REVIEW": f">= {thresholds['DEPOSIT_COUNT_REVIEW']}",
                "DEPOSIT_VOLUME_REVIEW": f">= {thresholds['DEPOSIT_VOLUME_REVIEW']}"}
    if expected["DEPOSIT_COUNT_REVIEW"] not in [row[1] for row in cells]:
        raise SystemExit("DMN deposit count threshold does not match policy JSON")
    if expected["DEPOSIT_VOLUME_REVIEW"] not in [row[2] for row in cells]:
        raise SystemExit("DMN deposit volume threshold does not match policy JSON")
    print("[Fluxnova] DMN input fields and synthetic thresholds match policy JSON")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
