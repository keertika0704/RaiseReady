package com.raiseready.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One slice of a breakdown: a label (an investor, a revenue category, an expense category) with its total.
 * {@code slot} (1 to 8) picks the chart colour, so a colour follows the thing it stands for;
 * 0 is grey, used for "Other" and for the founders' own equity.
 * {@code valueText} is the amount as it is shown (₹20 lakh, 10%), and {@code detail} is an optional extra line for the tooltip.
 */
public record BreakupSegment(String label, LocalDate firstDate, BigDecimal amountInr, BigDecimal sharePct, int slot,
                             String valueText, String detail) {

    BreakupSegment withDetail(String detail) {
        return new BreakupSegment(label, firstDate, amountInr, sharePct, slot, valueText, detail);
    }
}
