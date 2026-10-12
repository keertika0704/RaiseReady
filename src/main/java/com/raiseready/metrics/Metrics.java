package com.raiseready.metrics;

import java.math.BigDecimal;

/**
 * The founder's headline numbers, all in rupees. {@code monthlyBurn} is expenses minus revenue over the last 30 days,
 * so it is zero or negative when the company is earning at least what it spends. {@code runwayMonths} is null in
 * that case: see {@link #profitable()}.
 */
public record Metrics(BigDecimal cash, BigDecimal monthlyBurn, BigDecimal runwayMonths, BigDecimal mrr, int activeSubscriptions) {

    /** True when there is no burn to run out of, so there is no runway figure to show. */
    public boolean profitable() {
        return runwayMonths == null;
    }
}
