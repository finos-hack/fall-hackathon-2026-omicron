package org.finos.ai.demo.afc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Deterministic acceptance suite for the AML pipeline (AIGF mi-5).
 *
 * <p>These are the invariants the governance story depends on. They are
 * exercised here at runtime so the UI and the judges can see them pass, in
 * addition to the JUnit suite that runs in CI.
 */
@Service
public class AfcAcceptanceService {

  public record Check(String id, String description, boolean passed, String detail) {}

  public record Report(Instant generatedAt, int passed, int total, List<Check> checks) {}

  private final AfcRuleEngine ruleEngine;
  private final AfcPolicyMatrix policyMatrix;

  public AfcAcceptanceService(AfcRuleEngine ruleEngine, AfcPolicyMatrix policyMatrix) {
    this.ruleEngine = ruleEngine;
    this.policyMatrix = policyMatrix;
  }

  public Report run() {
    List<Check> checks = new ArrayList<>();

    AfcAlert highRisk = highRiskAlert();
    AfcPreScreenResult high = ruleEngine.screen(highRisk);
    checks.add(check(
        "high-risk-never-auto-closes",
        "A high-risk alert (score >= 70) must route to human review even when the agent recommends closing",
        high.score() >= 70
            && policyMatrix.decide(high.score(), "close") == AfcPolicyPath.MANDATORY_REVIEW
            && policyMatrix.decide(high.score(), "escalate") == AfcPolicyPath.MANDATORY_REVIEW,
        "score=" + high.score() + ", agent=close -> "
            + policyMatrix.decide(high.score(), "close")));

    AfcAlert mediumRisk = mediumRiskAlert();
    AfcPreScreenResult medium = ruleEngine.screen(mediumRisk);
    checks.add(check(
        "mid-band-always-reviews",
        "A mid-band alert (40-69) must require human review regardless of the agent recommendation",
        medium.score() >= 40 && medium.score() < 70
            && policyMatrix.decide(medium.score(), "close") == AfcPolicyPath.REVIEW_WITH_ADVISORY
            && policyMatrix.decide(medium.score(), "escalate") == AfcPolicyPath.REVIEW_WITH_ADVISORY,
        "score=" + medium.score()));

    AfcAlert lowRisk = lowRiskAlert();
    AfcPreScreenResult low = ruleEngine.screen(lowRisk);
    checks.add(check(
        "low-risk-agent-agreement-auto-closes",
        "A low-risk alert may auto-close only when the agent also recommends closing",
        low.score() < 40
            && policyMatrix.decide(low.score(), "close") == AfcPolicyPath.AUTO_CLOSE_ALLOWED,
        "score=" + low.score() + ", agent=close -> "
            + policyMatrix.decide(low.score(), "close")));

    checks.add(check(
        "agent-disagreement-escalates",
        "When the agent disagrees with a low rule score, the alert must go to a human",
        policyMatrix.decide(low.score(), "escalate") == AfcPolicyPath.REVIEW_ON_DISAGREEMENT,
        "agent=escalate -> " + policyMatrix.decide(low.score(), "escalate")));

    boolean deterministic = true;
    AfcPreScreenResult first = ruleEngine.screen(highRisk);
    for (int i = 0; i < 100; i++) {
      AfcPreScreenResult next = ruleEngine.screen(highRisk);
      if (next.score() != first.score() || !next.hits().equals(first.hits())) {
        deterministic = false;
        break;
      }
    }
    checks.add(check(
        "rule-engine-is-deterministic",
        "The rule engine must return identical scores and hits across 100 runs",
        deterministic,
        "100 runs compared"));

    int passed = (int) checks.stream().filter(Check::passed).count();
    return new Report(Instant.now(), passed, checks.size(), checks);
  }

  private static Check check(String id, String description, boolean passed, String detail) {
    return new Check(id, description, passed, detail);
  }

  private static AfcAlert highRiskAlert() {
    Instant t0 = Instant.parse("2026-11-10T09:00:00Z");
    return new AfcAlert(
        "ACC-HIGH", "CUST-1", "retail", true, true,
        List.of(
            cash("9500", t0),
            cash("9600", t0.plus(Duration.ofHours(2))),
            cash("9700", t0.plus(Duration.ofHours(4)))));
  }

  private static AfcAlert mediumRiskAlert() {
    Instant t0 = Instant.parse("2026-11-10T09:00:00Z");
    List<AfcTransaction> txs = new ArrayList<>(List.of(
        cash("9500", t0),
        cash("9600", t0.plus(Duration.ofHours(1))),
        cash("9700", t0.plus(Duration.ofHours(2)))));
    for (int i = 0; i < 7; i++) {
      txs.add(new AfcTransaction(
          new BigDecimal("100"), t0.plus(Duration.ofMinutes(30 + i)), "CARD", "US"));
    }
    return new AfcAlert("ACC-MED", "CUST-2", "retail", false, false, txs);
  }

  private static AfcAlert lowRiskAlert() {
    return new AfcAlert(
        "ACC-LOW", "CUST-3", "small-business", false, false,
        List.of(new AfcTransaction(
            new BigDecimal("120"), Instant.parse("2026-11-10T10:00:00Z"), "CARD", "US")));
  }

  private static AfcTransaction cash(String amount, Instant at) {
    return new AfcTransaction(new BigDecimal(amount), at, "CASH", "US");
  }
}
