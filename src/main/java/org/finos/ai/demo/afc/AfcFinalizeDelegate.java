package org.finos.ai.demo.afc;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** Terminal bookkeeping shared by both the reviewed and auto-closed paths. */
@Component("afcFinalizeDelegate")
public class AfcFinalizeDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    Object decision = execution.getVariable("afcFinalDecision");
    if (decision == null) {
      execution.setVariable("afcFinalDecision", "reviewed");
    }
    execution.setVariable("afcFinalized", true);
  }
}
