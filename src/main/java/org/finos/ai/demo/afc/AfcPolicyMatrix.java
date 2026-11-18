package org.finos.ai.demo.afc;

/**
 * Deterministic policy matrix mapping a rule score to a review path.
 * Every alert requires a human disposition. The agent recommendation is
 * recorded as advisory evidence and never affects routing.
 */
@org.springframework.stereotype.Component
public final class AfcPolicyMatrix {

  public static final int MANDATORY_REVIEW_THRESHOLD = 70;
  public static final int ADVISORY_REVIEW_THRESHOLD = 40;

  public AfcPolicyPath decide(int ruleScore, String agentRecommendation) {
    if (ruleScore >= MANDATORY_REVIEW_THRESHOLD) {
      return AfcPolicyPath.MANDATORY_REVIEW;
    }
    if (ruleScore >= ADVISORY_REVIEW_THRESHOLD) {
      return AfcPolicyPath.REVIEW_WITH_ADVISORY;
    }
    return AfcPolicyPath.HUMAN_REVIEW_REQUIRED;
  }

  /** Human-readable policy citation recorded in the evidence pack. */
  public String citation() {
    return "Policy AFC-3.2: score >= " + MANDATORY_REVIEW_THRESHOLD
        + " -> mandatory human review; score >= " + ADVISORY_REVIEW_THRESHOLD
        + " -> review with advisory; below -> human disposition required."
        + " Agent output cannot close an alert.";
  }
}
