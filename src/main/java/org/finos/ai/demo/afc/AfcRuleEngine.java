package org.finos.ai.demo.afc;

/**
 * Deterministic rule engine for AML alert pre-screening.
 *
 * <p>Deliberately pure: no LLM calls, no I/O, no clock reads. The same alert
 * always produces the same score and the same hits, which is what makes the
 * downstream policy gate auditable and what bounds agent non-determinism in
 * the pipeline (FINOS AIGF mi-5 acceptance invariants depend on this).
 */
public final class AfcRuleEngine {

  static final int STRUCTURING_POINTS = 35;
  static final int HIGH_RISK_JURISDICTION_POINTS = 20;
  static final int VELOCITY_POINTS = 15;
  static final int SANCTIONS_POINTS = 30;
  static final int PEP_POINTS = 20;
  static final int ROUND_AMOUNT_POINTS = 10;

  /** Reporting threshold (USD) used by the structuring rule. */
  static final java.math.BigDecimal REPORTING_THRESHOLD = new java.math.BigDecimal("10000");

  /** Cash deposits in this band are treated as attempted structuring. */
  static final java.math.BigDecimal STRUCTURING_LOWER_BOUND = new java.math.BigDecimal("9000");

  static final int STRUCTURING_MIN_COUNT = 3;
  static final int MAX_TRANSACTIONS_FOR_VELOCITY = 10;
  static final int ROUND_AMOUNT_MIN_COUNT = 3;

  private static final java.util.Set<String> HIGH_RISK_JURISDICTIONS =
      java.util.Set.of("IR", "KP", "SY", "CU", "RU", "BY", "MM", "AF");

  public AfcPreScreenResult screen(AfcAlert alert) {
    java.util.List<AfcRuleHit> hits = new java.util.ArrayList<>();
    java.util.List<AfcTransaction> transactions = alert.transactions();

    if (structuringPattern(transactions)) {
      hits.add(new AfcRuleHit(
          "structuring",
          "Multiple cash deposits just below the " + REPORTING_THRESHOLD + " reporting threshold",
          STRUCTURING_POINTS));
    }
    if (transactions.stream()
        .anyMatch(t -> HIGH_RISK_JURISDICTIONS.contains(upper(t.counterpartyCountry())))) {
      hits.add(new AfcRuleHit(
          "high-risk-jurisdiction",
          "Counterparty in a high-risk jurisdiction",
          HIGH_RISK_JURISDICTION_POINTS));
    }
    if (transactions.size() >= MAX_TRANSACTIONS_FOR_VELOCITY) {
      hits.add(new AfcRuleHit(
          "velocity",
          "Unusually high transaction velocity in the monitoring window",
          VELOCITY_POINTS));
    }
    if (alert.sanctionsNearMatch()) {
      hits.add(new AfcRuleHit(
          "sanctions-near-match",
          "Customer or counterparty is a near match on a sanctions list",
          SANCTIONS_POINTS));
    }
    if (alert.pep()) {
      hits.add(new AfcRuleHit(
          "pep",
          "Customer is a politically exposed person",
          PEP_POINTS));
    }
    if (roundAmountPattern(transactions)) {
      hits.add(new AfcRuleHit(
          "round-amounts",
          "Repeated round-amount transfers",
          ROUND_AMOUNT_POINTS));
    }

    int score = Math.min(100, hits.stream().mapToInt(AfcRuleHit::points).sum());
    return new AfcPreScreenResult(score, hits);
  }

  private static boolean structuringPattern(java.util.List<AfcTransaction> transactions) {
    java.util.List<AfcTransaction> cash = transactions.stream()
        .filter(t -> "CASH".equalsIgnoreCase(t.channel()))
        .filter(t -> t.amount() != null)
        .filter(t -> t.amount().compareTo(STRUCTURING_LOWER_BOUND) >= 0)
        .filter(t -> t.amount().compareTo(REPORTING_THRESHOLD) < 0)
        .sorted(java.util.Comparator.comparing(AfcTransaction::timestamp))
        .toList();
    for (int i = 0; i < cash.size(); i++) {
      java.time.Instant windowStart = cash.get(i).timestamp();
      java.time.Instant windowEnd = windowStart.plus(java.time.Duration.ofHours(24));
      long inWindow = cash.stream()
          .filter(t -> !t.timestamp().isBefore(windowStart) && t.timestamp().isBefore(windowEnd))
          .count();
      if (inWindow >= STRUCTURING_MIN_COUNT) {
        return true;
      }
    }
    return false;
  }

  private static boolean roundAmountPattern(java.util.List<AfcTransaction> transactions) {
    long round = transactions.stream()
        .map(AfcTransaction::amount)
        .filter(java.util.Objects::nonNull)
        .filter(a -> a.signum() > 0)
        .filter(a -> a.remainder(new java.math.BigDecimal("1000")).signum() == 0)
        .count();
    return round >= ROUND_AMOUNT_MIN_COUNT;
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
