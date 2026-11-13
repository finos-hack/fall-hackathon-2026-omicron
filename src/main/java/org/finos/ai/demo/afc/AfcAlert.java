package org.finos.ai.demo.afc;

import java.util.List;

/**
 * An AML alert as submitted by compliance operations. The transaction list is
 * expected to cover the monitoring window (typically the last 24 hours).
 */
public record AfcAlert(
    String alertId,
    String customerId,
    String customerSegment,
    boolean pep,
    boolean sanctionsNearMatch,
    List<AfcTransaction> transactions) {

  public AfcAlert {
    transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }
}
