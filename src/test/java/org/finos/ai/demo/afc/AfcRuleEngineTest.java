package org.finos.ai.demo.afc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AfcRuleEngineTest {

  private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

  private final AfcRuleEngine engine = new AfcRuleEngine();
  private final AfcPolicyMatrix matrix = new AfcPolicyMatrix();

  private static AfcTransaction cash(String amount, Instant at) {
    return new AfcTransaction(new BigDecimal(amount), at, "CASH", "US");
  }

  private static AfcTransaction transfer(String amount, Instant at, String country) {
    return new AfcTransaction(new BigDecimal(amount), at, "WIRE", country);
  }

  private static AfcAlert alert(boolean pep, boolean sanctions, List<AfcTransaction> txs) {
    return new AfcAlert("A-1", "C-1", "retail", pep, sanctions, txs);
  }

  @Test
  void structuringPatternScoresThirtyFive() {
    AfcAlert alert = alert(false, false, List.of(
        cash("9500", T0),
        cash("9600", T0.plus(Duration.ofHours(2))),
        cash("9700", T0.plus(Duration.ofHours(4)))));
    AfcPreScreenResult result = engine.screen(alert);
    assertEquals(35, result.score());
    assertTrue(result.hits().stream().anyMatch(h -> h.ruleId().equals("structuring")));
  }

  @Test
  void highRiskCombinationReachesMandatoryReviewBand() {
    List<AfcTransaction> txs = new ArrayList<>(List.of(
        cash("9500", T0),
        cash("9600", T0.plus(Duration.ofHours(2))),
        cash("9700", T0.plus(Duration.ofHours(4)))));
    AfcAlert alert = alert(true, true, txs);
    AfcPreScreenResult result = engine.screen(alert);
    assertEquals(85, result.score());
    assertEquals(AfcPolicyPath.MANDATORY_REVIEW, matrix.decide(result.score(), "close"));
  }

  @Test
  void agentCannotOverrideHighRiskScore() {
    assertEquals(AfcPolicyPath.MANDATORY_REVIEW, matrix.decide(85, "close"));
    assertEquals(AfcPolicyPath.MANDATORY_REVIEW, matrix.decide(85, "escalate"));
    assertEquals(AfcPolicyPath.MANDATORY_REVIEW, matrix.decide(70, "close"));
  }

  @Test
  void lowRiskWithAgentAgreementAllowsAutoClose() {
    AfcAlert alert = alert(false, false, List.of(transfer("100", T0, "US")));
    AfcPreScreenResult result = engine.screen(alert);
    assertEquals(0, result.score());
    assertEquals(AfcPolicyPath.AUTO_CLOSE_ALLOWED, matrix.decide(result.score(), "close"));
  }

  @Test
  void lowRiskWithAgentDisagreementEscalatesToHuman() {
    assertEquals(AfcPolicyPath.REVIEW_ON_DISAGREEMENT, matrix.decide(10, "escalate"));
    assertEquals(AfcPolicyPath.REVIEW_ON_DISAGREEMENT, matrix.decide(10, null));
    assertEquals(AfcPolicyPath.REVIEW_ON_DISAGREEMENT, matrix.decide(10, "garbage"));
  }

  @Test
  void midBandAlwaysRequiresHumanReview() {
    assertEquals(AfcPolicyPath.REVIEW_WITH_ADVISORY, matrix.decide(55, "close"));
    assertEquals(AfcPolicyPath.REVIEW_WITH_ADVISORY, matrix.decide(55, "escalate"));
  }

  @Test
  void screeningIsDeterministicAcrossRuns() {
    AfcAlert alert = alert(false, true, List.of(
        cash("9500", T0),
        cash("9600", T0.plus(Duration.ofHours(1))),
        cash("9700", T0.plus(Duration.ofHours(2))),
        transfer("2000", T0.plus(Duration.ofHours(3)), "IR")));
    AfcPreScreenResult first = engine.screen(alert);
    for (int i = 0; i < 100; i++) {
      AfcPreScreenResult next = engine.screen(alert);
      assertEquals(first.score(), next.score());
      assertEquals(first.hits(), next.hits());
    }
  }

  @Test
  void velocityAloneDoesNotForceReview() {
    List<AfcTransaction> txs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      txs.add(transfer("101", T0.plus(Duration.ofMinutes(i)), "US"));
    }
    AfcAlert alert = alert(false, false, txs);
    AfcPreScreenResult result = engine.screen(alert);
    assertEquals(15, result.score());
    assertFalse(result.hits().isEmpty());
    assertEquals(AfcPolicyPath.AUTO_CLOSE_ALLOWED, matrix.decide(result.score(), "close"));
  }
}
