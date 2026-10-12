package com.raiseready.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Investment(
        String companyName,
        String investorName,
        LocalDate investedOn,
        BigDecimal valuationInr,
        BigDecimal amountInr,
        BigDecimal equityPct) {
}
