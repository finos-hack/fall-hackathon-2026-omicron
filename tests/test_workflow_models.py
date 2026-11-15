import json
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"


class WorkflowModelTests(unittest.TestCase):
    def test_every_bpmn_path_waits_for_investigator(self):
        process = ET.parse(ROOT / "fluxnova/aml_alert_triage.bpmn").getroot()
        flows = process.findall(f".//{{{BPMN_NS}}}sequenceFlow")
        elements = list(process.findall(f".//{{{BPMN_NS}}}startEvent"))
        starts = {node.attrib["id"] for node in elements}
        ends = {node.attrib["id"] for node in process.findall(f".//{{{BPMN_NS}}}endEvent")}
        human_tasks = {node.attrib["id"] for node in process.findall(f".//{{{BPMN_NS}}}userTask")}
        edges = {}
        for flow in flows:
            edges.setdefault(flow.attrib["sourceRef"], []).append(flow.attrib["targetRef"])
        self.assertEqual({"investigatorReview"}, human_tasks)
        self.assertEqual(1, len(starts))
        pending = [(start, False) for start in starts]
        visited = set()
        while pending:
            node, saw_human = pending.pop()
            saw_human = saw_human or node in human_tasks
            state = (node, saw_human)
            if state in visited:
                continue
            visited.add(state)
            if node in ends:
                self.assertTrue(saw_human, "A process path reached the end without human review")
            pending.extend((target, saw_human) for target in edges.get(node, []))
        self.assertTrue(any(node in ends for node, _ in visited))

    def test_dmn_inputs_and_thresholds_match_policy(self):
        subprocess.run(
            [sys.executable, str(ROOT / "scripts/check_dmn_policy.py")],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_calm_mappings_are_labeled_as_partial(self):
        model = json.loads((ROOT / "calm/architecture/aml_alert_triage.architecture.json").read_text())
        requirements = model["controls"]["aml-governance-controls"]["requirements"]
        mappings = {item["config"]["control-id"]: item["config"]["description"] for item in requirements}
        self.assertIn("CCC.Core.CN04", mappings)
        self.assertIn("partial", mappings["CCC.Core.CN04"].lower())
        self.assertIn("CCC.Core.CN09", mappings)
        self.assertIn("partial", mappings["CCC.Core.CN09"].lower())


if __name__ == "__main__":
    unittest.main()
