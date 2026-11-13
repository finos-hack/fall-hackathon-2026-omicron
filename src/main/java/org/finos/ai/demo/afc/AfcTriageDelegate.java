package org.finos.ai.demo.afc;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** Runs the LLM triage step and records its (advisory) recommendation. */
@Component("afcTriageDelegate")
public class AfcTriageDelegate implements JavaDelegate {

  private final AfcLlmTriageService triageService;

  public AfcTriageDelegate(AfcLlmTriageService triageService) {
    this.triageService = triageService;
  }

  @Override
  public void execute(DelegateExecution execution) {
    String alertJson = (String) execution.getVariable("afcAlertJson");
    int score = (Integer) execution.getVariable("afcRuleScore");
    String summary = (String) execution.getVariable("afcRuleSummary");
    AfcLlmTriageService.TriageOutcome outcome = triageService.triage(alertJson, score, summary);
    execution.setVariable("afcAgentRecommendation", outcome.recommendation());
    execution.setVariable("afcAgentRationale", outcome.rationale());
    execution.setVariable("afcDlpRedactions", outcome.redactionCount());
    execution.setVariable("afcPromptTokens", outcome.promptTokens());
    execution.setVariable("afcCompletionTokens", outcome.completionTokens());
  }
}
