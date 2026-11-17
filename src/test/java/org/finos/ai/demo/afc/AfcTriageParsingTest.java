package org.finos.ai.demo.afc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AfcTriageParsingTest {

  @Test
  void strictJsonIsParsed() {
    AfcLlmTriageService.Parsed parsed = AfcLlmTriageService.parse(
        "{\"recommendation\":\"close\",\"rationale\":\"No indicators.\"}");
    assertEquals("close", parsed.recommendation());
    assertEquals("No indicators.", parsed.rationale());
    assertEquals("strict-json", parsed.mode());
  }

  @Test
  void fencedJsonIsParsed() {
    AfcLlmTriageService.Parsed parsed = AfcLlmTriageService.parse(
        "```json\n{\"recommendation\":\"escalate\",\"rationale\":\"PEP hit.\"}\n```");
    assertEquals("escalate", parsed.recommendation());
    assertEquals("strict-json", parsed.mode());
  }

  @Test
  void malformedJsonFallsBackToLenientExtraction() {
    String malformed = "{\"recommendation\":\"escalate\",\"rationale\":\"Structuring pattern.\","
        + "\"cited_evidence\":[\"pep\":true]}";
    AfcLlmTriageService.Parsed parsed = AfcLlmTriageService.parse(malformed);
    assertEquals("escalate", parsed.recommendation());
    assertEquals("Structuring pattern.", parsed.rationale());
    assertEquals("lenient-regex", parsed.mode());
  }

  @Test
  void unparseableOutputDefaultsToEscalation() {
    AfcLlmTriageService.Parsed parsed = AfcLlmTriageService.parse("The alert looks fine to me.");
    assertEquals("escalate", parsed.recommendation());
    assertEquals("safe-default", parsed.mode());
  }

  @Test
  void unknownRecommendationDefaultsToEscalation() {
    AfcLlmTriageService.Parsed parsed = AfcLlmTriageService.parse(
        "{\"recommendation\":\"maybe\",\"rationale\":\"Unsure\"}");
    assertEquals("escalate", parsed.recommendation());
    assertEquals("safe-default", parsed.mode());
  }
}
