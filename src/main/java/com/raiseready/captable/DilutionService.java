package com.raiseready.captable;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

/**
 * The arithmetic of an equity round, and nothing else: no database, so it is the same sum whoever asks.
 * <ul>
 *   <li>Post-money = pre-money + investment.</li>
 *   <li>Equity = investment / post-money.</li>
 *   <li>New shares = shares before the round * investment / pre-money. Existing holders keep their shares, so they
 *       are diluted: the new shares are the same share of the new total as the equity above.</li>
 * </ul>
 */
@Service
public class DilutionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param existingShares every share that exists before this round
     * @throws IllegalArgumentException if an amount or the share count is not positive
     * @throws ArithmeticException      if the new shares would not fit in a long (a pre-money absurdly small for the investment)
     */
    public Dilution calculate(BigDecimal preMoneyInr, BigDecimal investmentInr, long existingShares) {
        if (preMoneyInr.signum() <= 0 || investmentInr.signum() <= 0 || existingShares <= 0) {
            throw new IllegalArgumentException("Pre-money, investment and existing shares must all be more than zero.");
        }
        BigDecimal postMoney = preMoneyInr.add(investmentInr);
        BigDecimal equityPct = investmentInr.multiply(HUNDRED).divide(postMoney, 2, RoundingMode.HALF_UP);
        long newShares = BigDecimal.valueOf(existingShares).multiply(investmentInr)
                .divide(preMoneyInr, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Dilution(postMoney, equityPct, newShares);
    }
}
