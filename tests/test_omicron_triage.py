import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from omicron.triage import evaluate_alert, record_human_disposition  # noqa: E402


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


class TriageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = load(ROOT / "policy/aml_triage_policy.json")
        cls.low = load(ROOT / "examples/alerts/low.json")
        cls.medium = load(ROOT / "examples/alerts/medium.json")
        cls.high = load(ROOT / "examples/alerts/high_with_conflicting_advisory.json")

    def test_synthetic_scenarios_get_expected_priority(self):
        self.assertEqual("LOW", evaluate_alert(self.low, self.policy)["priority"])
        self.assertEqual("MEDIUM", evaluate_alert(self.medium, self.policy)["priority"])
        self.assertEqual("HIGH", evaluate_alert(self.high, self.policy, self.high["advisory"])["priority"])

    def test_every_priority_waits_for_a_person(self):
        for alert in (self.low, self.medium, self.high):
            result = evaluate_alert(alert, self.policy, alert.get("advisory"))
            self.assertTrue(result["human_review"]["required"])
            self.assertIsNone(result["human_review"]["disposition"])
            self.assertFalse(result["human_review"]["alert_closure_by_ai_allowed"])
            self.assertFalse(result["human_review"]["report_filing_by_ai_allowed"])

    def test_conflicting_advisory_cannot_change_priority_or_disposition(self):
        with_advisory = evaluate_alert(self.high, self.policy, self.high["advisory"])
        without_advisory_alert = copy.deepcopy(self.high)
        without_advisory_alert.pop("advisory")
        without_advisory = evaluate_alert(without_advisory_alert, self.policy)
        self.assertEqual(without_advisory["priority"], with_advisory["priority"])
        self.assertEqual(without_advisory["score"], with_advisory["score"])
        self.assertIsNone(with_advisory["human_review"]["disposition"])
        self.assertNotIn("Ignore the policy", json.dumps(with_advisory))

    def test_candidate_match_is_not_a_legal_conclusion(self):
        alert = copy.deepcopy(self.low)
        alert["screening_candidate_match"] = True
        result = evaluate_alert(alert, self.policy)
        self.assertEqual("HIGH", result["priority"])
        self.assertIsNone(result["legal_conclusion"])
        self.assertTrue(result["human_review"]["required"])

    def test_missing_timezone_fails_closed(self):
        alert = copy.deepcopy(self.low)
        alert["transactions"][0]["occurred_at"] = "2026-11-14T10:00:00"
        with self.assertRaisesRegex(ValueError, "include a time zone"):
            evaluate_alert(alert, self.policy)

    def test_invalid_transaction_currency_fails_closed(self):
        alert = copy.deepcopy(self.low)
        alert["transactions"][0]["currency"] = "EUR"
        with self.assertRaisesRegex(ValueError, "Only USD"):
            evaluate_alert(alert, self.policy)

    def test_human_event_is_hash_chained_and_does_not_store_raw_alert(self):
        result = evaluate_alert(self.high, self.policy, self.high["advisory"])
        event = record_human_disposition(result, "demo-investigator", "escalate", "prior-digest")
        self.assertEqual("prior-digest", event["previous_event_hash"])
        self.assertEqual("escalate", event["disposition"])
        self.assertIn("event_sha256", event)
        self.assertNotIn("transactions", json.dumps(event))
        self.assertNotIn("Ignore the policy", json.dumps(event))

    def test_assistant_cannot_file_report_as_disposition(self):
        result = evaluate_alert(self.high, self.policy, self.high["advisory"])
        with self.assertRaisesRegex(ValueError, "Disposition must be one of"):
            record_human_disposition(result, "demo-investigator", "file_report")


if __name__ == "__main__":
    unittest.main()
