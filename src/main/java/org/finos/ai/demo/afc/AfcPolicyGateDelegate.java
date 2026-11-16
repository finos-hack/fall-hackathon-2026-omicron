package org.finos.ai.demo.afc;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Applies the deterministic policy matrix to (rule score, agent recommendation)
 * and writes the routing path. This is the control that bounds agent
 * non-determinism: the agent can add human oversight but never remove it.
 */
@Component("afcPolicyGateDelegate")
public class AfcPolicyGateDelegate implements JavaDelegate {

  private final AfcPolicyMatrix matrix;

  public AfcPolicyGateDelegate(AfcPolicyMatrix matrix) {
    this.matrix = matrix;
  }

  @Override
  public void execute(DelegateExecution execution) {
    int score = (Integer) execution.getVariable("afcRuleScore");
    String recommendation = (String) execution.getVariable("afcAgentRecommendation");
    AfcPolicyPath path = matrix.decide(score, recommendation);
    execution.setVariable("afcPolicyPath", path.name());
    execution.setVariable("afcPolicyCitation", matrix.citation());
    execution.setVariable(
        "afcGateRationale",
        "score=" + score + ", agent=" + recommendation + " -> " + path.name());
  }
}
