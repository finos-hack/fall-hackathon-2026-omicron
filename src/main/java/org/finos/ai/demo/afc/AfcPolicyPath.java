package org.finos.ai.demo.afc;

/** The deterministic routing decision for an alert, independent of the agent. */
public enum AfcPolicyPath {
  /** Rule score is high: human review is mandatory, whatever the agent said. */
  MANDATORY_REVIEW,
  /** Mid-band score: human review required, agent recommendation attached as advisory. */
  REVIEW_WITH_ADVISORY,
  /** Low score and agent agrees: auto-close is permitted (still sampled by QA). */
  AUTO_CLOSE_ALLOWED,
  /** Low score but agent disagrees: escalate to a human rather than silently closing. */
  REVIEW_ON_DISAGREEMENT
}
