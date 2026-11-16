package org.finos.ai.demo.afc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Tool-using triage agent. The model only sees a redacted alert (AIGF mi-1 is
 * enforced here, at the egress boundary) and may call read-only investigation
 * tools. Its output is advisory: a parse failure, malformed answer, or model
 * error is downgraded to "escalate", never to a close recommendation.
 */
@Service
public class AfcLlmTriageService {

  private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{9,}\\b");
  private static final Pattern RECOMMENDATION =
      Pattern.compile("\"recommendation\"\\s*:\\s*\"(close|escalate)\"");
  private static final Pattern RATIONALE =
      Pattern.compile("\"rationale\"\\s*:\\s*\"([^\"]{0,500})\"");

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AfcLlmTriageService.class);

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public AfcLlmTriageService(
      @org.springframework.beans.factory.annotation.Qualifier("openAiChatModel")
          org.springframework.ai.chat.model.ChatModel chatModel,
      ObjectMapper objectMapper) {
    this.chatClient = ChatClient.builder(chatModel).build();
    this.objectMapper = objectMapper;
  }

  public record TriageOutcome(
      String recommendation,
      String rationale,
      String redactedAlertJson,
      int redactionCount,
      int promptTokens,
      int completionTokens,
      List<String> toolCalls,
      String parseMode) {}

  /** Result of turning raw model text into a recommendation. Unit tested. */
  public record Parsed(String recommendation, String rationale, String mode) {}

  public TriageOutcome triage(String alertJson, int ruleScore, String ruleSummary) {
    StringBuilder redactions = new StringBuilder();
    String redacted = redact(alertJson, redactions);

    String userPrompt = """
        Alert (redacted):
        %s

        Deterministic pre-screen score: %d (%s)

        You may call the read-only investigation tools if they would help.
        Before recommending, call at least one investigation tool to gather evidence.
        Then respond with JSON only, exactly this shape:
        {"recommendation":"close|escalate","rationale":"one or two sentences"}
        """.formatted(redacted, ruleScore, ruleSummary);

    AfcInvestigationTools tools = new AfcInvestigationTools(parseAlert(alertJson));
    ChatResponse response = callWithRetry(userPrompt, tools);
    String content = response == null || response.getResult() == null
        ? ""
        : response.getResult().getOutput().getText();
    LOG.info("AFC triage raw model content: {}", content);

    Parsed parsed = parse(content);
    int promptTokens = 0;
    int completionTokens = 0;
    if (response != null && response.getMetadata() != null
        && response.getMetadata().getUsage() != null) {
      Usage usage = response.getMetadata().getUsage();
      promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
      completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
    }

    return new TriageOutcome(
        parsed.recommendation(),
        parsed.rationale(),
        redacted,
        redactions.length(),
        promptTokens,
        completionTokens,
        tools.invocations(),
        parsed.mode());
  }

  private ChatResponse callWithRetry(String userPrompt, AfcInvestigationTools tools) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        return chatClient.prompt()
            .system("""
                You are an AML alert triage analyst at a regulated bank. You assess
                transaction-monitoring alerts and recommend whether to close them or
                escalate for investigation. Use the investigation tools when useful.
                Your recommendation is advisory: a deterministic policy gate takes the
                final routing decision.
                """)
            .user(userPrompt + "\n/no_think")
            .tools(tools)
            .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                .maxTokens(700)
                .temperature(0.4))
            .call()
            .chatResponse();
      } catch (RuntimeException e) {
        last = e;
        LOG.warn("AFC triage model call failed (attempt {}): {}", attempt, e.toString());
      }
    }
    LOG.error("AFC triage model call failed twice; defaulting to escalation", last);
    return null;
  }

  /** Strict JSON, then lenient field extraction, then safe default. */
  public static Parsed parse(String content) {
    String text = content == null ? "" : content;
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode node = mapper.readTree(stripFences(text));
      String candidate = node.path("recommendation").asText("").trim().toLowerCase();
      if ("close".equals(candidate) || "escalate".equals(candidate)) {
        return new Parsed(candidate, node.path("rationale").asText(""), "strict-json");
      }
    } catch (Exception ignored) {
      // fall through to lenient extraction
    }
    java.util.regex.Matcher rec = RECOMMENDATION.matcher(text);
    if (rec.find()) {
      java.util.regex.Matcher why = RATIONALE.matcher(text);
      String rationale = why.find() ? why.group(1) : "Parsed leniently from model output.";
      return new Parsed(rec.group(1), rationale, "lenient-regex");
    }
    return new Parsed("escalate", "Model response could not be parsed; treated as escalation.", "safe-default");
  }

  private AfcAlert parseAlert(String alertJson) {
    try {
      return objectMapper.readValue(alertJson, AfcAlert.class);
    } catch (Exception e) {
      return new AfcAlert("unknown", "unknown", "unknown", false, false, List.of());
    }
  }

  private static String redact(String json, StringBuilder notes) {
    var matcher = LONG_DIGITS.matcher(json);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, "[REDACTED]");
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
