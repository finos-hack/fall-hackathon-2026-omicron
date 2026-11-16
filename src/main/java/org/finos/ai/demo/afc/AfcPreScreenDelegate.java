package org.finos.ai.demo.afc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Deterministic pre-screen step: parses the submitted alert, runs the pure
 * rule engine, and writes the score and rule summary into process variables.
 */
@Component("afcPreScreenDelegate")
public class AfcPreScreenDelegate implements JavaDelegate {

  private final AfcRuleEngine ruleEngine;
  private final ObjectMapper objectMapper;

  public AfcPreScreenDelegate(AfcRuleEngine ruleEngine, ObjectMapper objectMapper) {
    this.ruleEngine = ruleEngine;
    this.objectMapper = objectMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    String alertJson = (String) execution.getVariable("afcAlertJson");
    AfcAlert alert;
    try {
      alert = objectMapper.readValue(alertJson, AfcAlert.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid AFC alert payload", e);
    }
    AfcPreScreenResult result = ruleEngine.screen(alert);
    execution.setVariable("afcAlertId", alert.alertId());
    execution.setVariable("afcCustomerId", alert.customerId());
    execution.setVariable("afcRuleScore", result.score());
    execution.setVariable("afcRuleSummary", result.summary());
    execution.setVariable("afcRuleHitCount", result.hits().size());
  }
}
