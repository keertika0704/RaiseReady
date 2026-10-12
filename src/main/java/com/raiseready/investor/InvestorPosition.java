package com.raiseready.investor;

import java.math.BigDecimal;

/**
 * What one investor has put in and what that is worth now.
 *
 * @param totalInvestedInr all the money they have put in through equity investments
 * @param shares           how many shares they hold
 * @param equityPct        their share of the company in percent, to two decimal places
 * @param currentValueInr  their equity times the company's current valuation; null until there is a valuation
 */
public record InvestorPosition(String name, BigDecimal totalInvestedInr, long shares, BigDecimal equityPct, BigDecimal currentValueInr) {
}
