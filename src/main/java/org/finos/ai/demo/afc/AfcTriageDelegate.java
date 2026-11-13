package org.finos.ai.demo.afc;

import org.finos.ai.demo.tokenomics.TokenomicsService;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Runs the LLM triage step and records its (advisory) recommendation. */
@Component("afcTriageDelegate")
public class AfcTriageDelegate implements JavaDelegate {

  private final AfcLlmTriageService triageService;
  private final ObjectProvider<TokenomicsService> tokenomicsService;
  private final Environment environment;

  public AfcTriageDelegate(
      AfcLlmTriageService triageService,
      ObjectProvider<TokenomicsService> tokenomicsService,
      Environment environment) {
    this.triageService = triageService;
    this.tokenomicsService = tokenomicsService;
    this.environment = environment;
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

    TokenomicsService tokenomics = tokenomicsService.getIfAvailable();
    if (tokenomics != null) {
      String model = environment.getProperty("spring.ai.openai.chat.options.model", "unknown");
      tokenomics.onLlmCall(
          AfcDemoService.PROCESS_KEY,
          "afc-triage",
          model,
          "openai",
          new TokenUsage(
              outcome.promptTokens(),
              outcome.completionTokens(),
              outcome.promptTokens() + outcome.completionTokens()));
    }
  }
}
