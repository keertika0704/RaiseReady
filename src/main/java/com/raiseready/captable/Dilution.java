package com.raiseready.captable;

import java.math.BigDecimal;

/**
 * What one equity investment does to the company.
 *
 * @param postMoneyValuationInr what the company is worth right after the money came in: pre-money + investment
 * @param equityPct             the share of the company the investment bought, in percent, to two decimal places
 * @param newShares             the whole shares created for the investor
 */
public record Dilution(BigDecimal postMoneyValuationInr, BigDecimal equityPct, long newShares) {
}
