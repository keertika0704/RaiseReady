package com.raiseready.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A saved row of the transactions table. */
public record Transaction(
        long id,
        TransactionType type,
        TransactionCategory category,
        LocalDate occurredOn,
        BigDecimal amountInr,
        String description,
        String investorName,
        BigDecimal equityPct,
        BigDecimal preMoneyValuationInr,
        Long sharesIssued,
        boolean recurring,
        Instant createdAt) {

    /**
     * What the whole company was worth right after this investment: the pre-money plus the money put in.
     * Null when there is no pre-money on the row (anything but an equity investment).
     */
    public BigDecimal postMoneyValuationInr() {
        return preMoneyValuationInr == null ? null : preMoneyValuationInr.add(amountInr);
    }
}
