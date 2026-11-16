package org.finos.ai.demo.afc;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single transaction attached to an AML alert. Amounts are in the account's
 * currency; timestamps are UTC.
 */
public record AfcTransaction(
    BigDecimal amount,
    Instant timestamp,
    String channel,
    String counterpartyCountry) {}
