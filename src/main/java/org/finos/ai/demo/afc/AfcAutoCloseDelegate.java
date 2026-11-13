package org.finos.ai.demo.afc;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** Auto-close path: only reachable when rules and agent both allow closing. */
@Component("afcAutoCloseDelegate")
public class AfcAutoCloseDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable("afcFinalDecision", "closed");
    execution.setVariable("afcReviewer", "policy:auto-close");
    execution.setVariable(
        "afcReviewRationale",
        "Rule score below review threshold and agent agreed to close; sampled for QA.");
  }
}
