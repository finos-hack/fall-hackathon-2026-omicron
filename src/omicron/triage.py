"""Deterministic policy gate for synthetic AML alert triage."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from typing import Any


def canonical_digest(value: Any) -> str:
    content = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _timestamp(value: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise ValueError("observed_at and transaction times must be ISO timestamps")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        moment = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise ValueError(f"Invalid ISO timestamp: {value}") from error
    if moment.tzinfo is None:
        raise ValueError("Timestamps must include a time zone")
    return moment.astimezone(timezone.utc)


def _money(value: Any) -> Decimal:
    try:
        amount = Decimal(str(value))
    except (InvalidOperation, ValueError) as error:
        raise ValueError("Transaction amount must be a decimal value") from error
    if not amount.is_finite() or amount < 0:
        raise ValueError("Transaction amount must be finite and nonnegative")
    return amount


def _measurements(alert: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    if not alert.get("case_id"):
        raise ValueError("case_id is required")
    observed_at = _timestamp(alert.get("observed_at"))
    transactions = alert.get("transactions")
    if not isinstance(transactions, list):
        raise ValueError("transactions must be a list")
    screening_match = alert.get("screening_candidate_match")
    if not isinstance(screening_match, bool):
        raise ValueError("screening_candidate_match must be true or false")

    start = observed_at - timedelta(hours=int(policy["lookback_hours"]))
    deposits: list[Decimal] = []
    for transaction in transactions:
        if not isinstance(transaction, dict):
            raise ValueError("Each transaction must be an object")
        time = _timestamp(transaction.get("occurred_at"))
        amount = _money(transaction.get("amount"))
        currency = transaction.get("currency")
        if currency != policy["currency"]:
            raise ValueError(f"Only {policy['currency']} synthetic examples are supported")
        if transaction.get("type") == "cash_deposit" and start <= time <= observed_at:
            deposits.append(amount)

    return {
        "deposit_count_24h": len(deposits),
        "deposit_total_24h_usd": sum(deposits, Decimal("0")),
        "screening_candidate_match": screening_match,
    }


def evaluate_alert(alert: dict[str, Any], policy: dict[str, Any], advisory: Any = None) -> dict[str, Any]:
    """Score a synthetic alert. Advisory content is deliberately not retained or used."""
    measured = _measurements(alert, policy)
    reasons: list[dict[str, Any]] = []
    score = 0
    for rule in policy["rules"]:
        observed = measured[rule["metric"]]
        if rule["operator"] == "greater_than_or_equal":
            matched = observed >= rule["threshold"]
        elif rule["operator"] == "equals":
            matched = observed == rule["expected"]
        else:
            raise ValueError(f"Unsupported policy operator: {rule['operator']}")
        if matched:
            score += int(rule["points"])
            reasons.append({"rule_id": rule["id"], "title": rule["title"], "points": rule["points"]})

    bands = sorted(policy["risk_bands"], key=lambda item: item["minimum_score"], reverse=True)
    tier = next(item["tier"] for item in bands if score >= item["minimum_score"])
    advisory_present = advisory is not None and advisory != ""
    return {
        "case_id": alert["case_id"],
        "policy_id": policy["policy_id"],
        "policy_version": policy["version"],
        "synthetic": True,
        "score": score,
        "priority": tier,
        "measurements": {
            "deposit_count_24h": measured["deposit_count_24h"],
            "deposit_total_24h_usd": str(measured["deposit_total_24h_usd"]),
            "screening_candidate_match": measured["screening_candidate_match"],
        },
        "reason_codes": reasons,
        "input_sha256": canonical_digest(alert),
        "advisory": {
            "present": bool(advisory_present),
            "used_for_score": False,
            "used_for_disposition": False,
            "raw_text_saved": False,
        },
        "human_review": {
            "required": True,
            "disposition": None,
            "alert_closure_by_ai_allowed": False,
            "report_filing_by_ai_allowed": False,
        },
        "legal_conclusion": None,
        "status": "AWAITING_HUMAN_REVIEW",
    }


def record_human_disposition(
    evaluation: dict[str, Any], reviewer_id: str, disposition: str, previous_event_hash: str | None = None
) -> dict[str, Any]:
    allowed = {"close_as_false_positive", "escalate", "request_information"}
    if not reviewer_id or len(reviewer_id) > 80:
        raise ValueError("A reviewer identifier of 1 to 80 characters is required")
    if disposition not in allowed:
        raise ValueError(f"Disposition must be one of: {', '.join(sorted(allowed))}")
    event = {
        "event_type": "human_disposition_recorded",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "case_id": evaluation["case_id"],
        "input_sha256": evaluation["input_sha256"],
        "policy_id": evaluation["policy_id"],
        "policy_version": evaluation["policy_version"],
        "priority_at_review": evaluation["priority"],
        "reviewer_id": reviewer_id,
        "disposition": disposition,
        "previous_event_hash": previous_event_hash,
    }
    event["event_sha256"] = canonical_digest(event)
    return event
