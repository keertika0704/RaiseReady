package com.raiseready.captable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Who owns what right now. {@code currentValuationInr} is the company's latest post-money valuation, null if
 * there has been no equity round.
 */
public record CapTable(List<Holding> holdings, long totalShares, BigDecimal currentValuationInr) {

    /** {@code pct} is the holder's share of all shares, in percent, to two decimal places. */
    public record Holding(String name, long shares, BigDecimal pct) {
    }
}
