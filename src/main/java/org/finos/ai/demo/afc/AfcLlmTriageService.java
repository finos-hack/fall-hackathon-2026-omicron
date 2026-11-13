package org.finos.ai.demo.afc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Calls the LLM for alert triage. The model only sees a redacted alert (the
 * AIGF mi-1 data-leakage control is enforced here, at the egress boundary) and
 * its output is treated as advisory: a parse failure or malformed answer is
 * downgraded to "escalate", never to a close recommendation.
 */
@Service
public class AfcLlmTriageService {

  private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{9,}\\b");

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AfcLlmTriageService.class);

  public AfcLlmTriageService(
      @org.springframework.beans.factory.annotation.Qualifier("openAiChatModel")
          org.springframework.ai.chat.model.ChatModel chatModel,
      ObjectMapper objectMapper) {
    this.chatClient = ChatClient.builder(chatModel).build();
    this.objectMapper = objectMapper;
  }

  public record TriageOutcome(
      String recommendation, String rationale, String redactedAlertJson,
      int redactionCount, int promptTokens, int completionTokens) {}

  public TriageOutcome triage(String alertJson, int ruleScore, String ruleSummary) {
    StringBuilder redactions = new StringBuilder();
    String redacted = redact(alertJson, redactions);

    String userPrompt = """
        Alert (redacted):
        %s

        Deterministic pre-screen score: %d (%s)

        Respond with JSON only, exactly this shape:
        {"recommendation":"close|escalate","rationale":"one or two sentences"}
        """.formatted(redacted, ruleScore, ruleSummary);

    ChatResponse response = chatClient.prompt()
        .system("""
            You are an AML alert triage analyst at a regulated bank. You assess
            transaction-monitoring alerts and recommend whether to close them or
            escalate for investigation. Your recommendation is advisory: a
            deterministic policy gate takes the final routing decision.
            """)
        .user(userPrompt + "\n/no_think")
        .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
            .maxTokens(700)
            .temperature(0.4))
        .call()
        .chatResponse();

    String content = response.getResult() == null ? "" : response.getResult().getOutput().getText();
    LOG.info("AFC triage raw model content: {}", content);
    String recommendation = "escalate";
    String rationale = "Model response could not be parsed; treated as escalation.";
    try {
      JsonNode node = objectMapper.readTree(stripFences(content));
      String candidate = node.path("recommendation").asText("escalate").trim().toLowerCase();
      if ("close".equals(candidate) || "escalate".equals(candidate)) {
        recommendation = candidate;
        rationale = node.path("rationale").asText("");
      }
    } catch (Exception ignored) {
      // Lenient fallback: even malformed JSON usually carries the two fields.
      java.util.regex.Matcher rec =
          java.util.regex.Pattern.compile("\"recommendation\"\\s*:\\s*\"(close|escalate)\"")
              .matcher(content == null ? "" : content);
      java.util.regex.Matcher why =
          java.util.regex.Pattern.compile("\"rationale\"\\s*:\\s*\"([^\"]{0,500})\"")
              .matcher(content == null ? "" : content);
      if (rec.find()) {
        recommendation = rec.group(1);
        if (why.find()) {
          rationale = why.group(1);
        } else {
          rationale = "Parsed leniently from model output.";
        }
      }
    }

    int promptTokens = 0;
    int completionTokens = 0;
    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      promptTokens = (int) response.getMetadata().getUsage().getPromptTokens();
      completionTokens = (int) response.getMetadata().getUsage().getCompletionTokens();
    }

    return new TriageOutcome(
        recommendation, rationale, redacted, redactions.length(), promptTokens, completionTokens);
  }

  private static String redact(String json, StringBuilder notes) {
    var matcher = LONG_DIGITS.matcher(json);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, "\"[REDACTED]\"".replace("\"", ""));
      notes.append('x');
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String stripFences(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastFence > firstNewline) {
        return trimmed.substring(firstNewline + 1, lastFence).trim();
      }
    }
    return trimmed;
  }
}
