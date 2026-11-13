package org.finos.ai.demo.afc;

import java.util.Locale;

/**
 * Deterministic policy matrix mapping (rule score, agent recommendation) to a
 * routing path. The agent recommendation can only ever move an alert toward
 * MORE human oversight, never less: that is the "bound non-determinism with
 * deterministic controls" property the governance story rests on.
 */
public final class AfcPolicyMatrix {

  public static final int MANDATORY_REVIEW_THRESHOLD = 70;
  public static final int ADVISORY_REVIEW_THRESHOLD = 40;

  public AfcPolicyPath decide(int ruleScore, String agentRecommendation) {
    String recommendation = agentRecommendation == null
        ? ""
        : agentRecommendation.trim().toLowerCase(Locale.ROOT);

    if (ruleScore >= MANDATORY_REVIEW_THRESHOLD) {
      return AfcPolicyPath.MANDATORY_REVIEW;
    }
    if (ruleScore >= ADVISORY_REVIEW_THRESHOLD) {
      return AfcPolicyPath.REVIEW_WITH_ADVISORY;
    }
    boolean agentWantsClose = "close".equals(recommendation);
    if (agentWantsClose) {
      return AfcPolicyPath.AUTO_CLOSE_ALLOWED;
    }
    // Missing, malformed, or "escalate" recommendations all err toward review.
    return AfcPolicyPath.REVIEW_ON_DISAGREEMENT;
  }

  /** Human-readable policy citation recorded in the evidence pack. */
  public String citation() {
    return "Policy AFC-3.2: score >= " + MANDATORY_REVIEW_THRESHOLD
        + " -> mandatory human review; score >= " + ADVISORY_REVIEW_THRESHOLD
        + " -> review with advisory; below -> auto-close only when the agent agrees,"
        + " otherwise review on disagreement.";
  }
}
