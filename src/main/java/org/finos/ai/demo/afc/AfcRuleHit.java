package org.finos.ai.demo.afc;

/** One triggered deterministic rule: stable id, human-readable reason, points. */
public record AfcRuleHit(String ruleId, String description, int points) {}
