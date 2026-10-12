package com.raiseready.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the service is asked to add. Not tied to any form, so an edit form or a test can build one too. */
public record NewTransaction(
        TransactionType type,
        TransactionCategory category,
        LocalDate occurredOn,
        BigDecimal amountInr,
        String description,
        String investorName,
        BigDecimal preMoneyValuationInr) {
}
