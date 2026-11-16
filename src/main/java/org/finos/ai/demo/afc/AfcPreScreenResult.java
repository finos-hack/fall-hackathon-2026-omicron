package org.finos.ai.demo.afc;

import java.util.List;

/** Deterministic pre-screen outcome: bounded score (0-100) plus the rule hits. */
public record AfcPreScreenResult(int score, List<AfcRuleHit> hits) {

  public AfcPreScreenResult {
    hits = hits == null ? List.of() : List.copyOf(hits);
  }

  public String summary() {
    if (hits.isEmpty()) {
      return "no deterministic rules triggered";
    }
    return hits.stream()
        .map(h -> h.ruleId() + " (+" + h.points() + ")")
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }
}
