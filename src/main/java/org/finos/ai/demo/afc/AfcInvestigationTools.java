package org.finos.ai.demo.afc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Read-only investigation tools the triage agent may call. Deliberately
 * deterministic and side-effect free: the model can look things up, but it
 * cannot change state. Every invocation is recorded so the evidence pack can
 * show what the agent actually did.
 */
public class AfcInvestigationTools {

  private final AfcAlert alert;
  private final List<String> invocations = new ArrayList<>();

  public AfcInvestigationTools(AfcAlert alert) {
    this.alert = alert;
  }

  @Tool(description = "Return the customer profile for the alert's customer (segment, account age, home country, prior alerts)")
  public String customerProfile() {
    invocations.add("customerProfile");
    return "{\"customerId\":\"" + alert.customerId() + "\",\"segment\":\""
        + alert.customerSegment() + "\",\"accountAgeMonths\":14,\"homeCountry\":\"US\","
        + "\"priorAlertsLast12Months\":3}";
  }

  @Tool(description = "Screen the customer against sanctions and politically-exposed-person lists")
  public String sanctionsScreening() {
    invocations.add("sanctionsScreening");
    return "{\"sanctionsNearMatch\":" + alert.sanctionsNearMatch()
        + ",\"politicallyExposedPerson\":" + alert.pep() + "}";
  }

  @Tool(description = "Summarise the alert's transactions: count, total amount, cash transaction count, distinct countries")
  public String transactionStats() {
    invocations.add("transactionStats");
    BigDecimal total = alert.transactions().stream()
        .map(AfcTransaction::amount)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    long cash = alert.transactions().stream()
        .filter(t -> "CASH".equalsIgnoreCase(t.channel()))
        .count();
    long countries = alert.transactions().stream()
        .map(AfcTransaction::counterpartyCountry)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .count();
    return "{\"count\":" + alert.transactions().size() + ",\"totalAmount\":" + total
        + ",\"cashCount\":" + cash + ",\"distinctCountries\":" + countries + "}";
  }

  public List<String> invocations() {
    return List.copyOf(invocations);
  }
}
