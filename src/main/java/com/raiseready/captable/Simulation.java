package com.raiseready.captable;

import java.math.BigDecimal;
import java.util.List;

/**
 * A pretend round laid over the real cap table. Nothing here was saved.
 *
 * @param dilution    what the round does, from {@link DilutionService}
 * @param rows        every real holder, then the pretend investor last
 * @param totalBefore every share that exists now
 * @param totalAfter  every share that would exist after the round
 */
public record Simulation(BigDecimal preMoneyInr, BigDecimal investmentInr, Dilution dilution, List<Row> rows, long totalBefore, long totalAfter) {

    /** Shares and ownership of one holder, before and after. The pretend investor has no shares before. */
    public record Row(String name, long sharesBefore, BigDecimal pctBefore, long sharesAfter, BigDecimal pctAfter, boolean pretend) {
    }
}
