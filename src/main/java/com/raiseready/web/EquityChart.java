package com.raiseready.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Who owns the company, by equity alone: each investor's equity, and what the founders still hold (in grey).
 * Equity is taken as bought in each round, added up. The founders hold the rest of the 100%.
 */
final class EquityChart {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    static final String FOUNDERS = "Founders";

    /** {@code segments} and {@code slices} are in the same order; {@code centreText} is the founders' share, shown in the middle. */
    record Result(List<BreakupSegment> segments, List<DonutSlice> slices, BigDecimal foundersPct, BigDecimal soldPct, String centreText) {
    }

    private EquityChart() {
    }

    static Result of(List<Investment> investments) {
        BigDecimal sold = investments.stream().map(Investment::equityPct).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal founders = HUNDRED.subtract(sold).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (investments.isEmpty() || sold.signum() <= 0) {
            return new Result(List.of(), List.of(), HUNDRED.setScale(2), BigDecimal.ZERO, pct(HUNDRED));
        }

        List<MoneyBreakup.Entry> entries = new ArrayList<>();
        investments.stream().sorted(Comparator.comparing(Investment::investedOn))
                .forEach(i -> entries.add(new MoneyBreakup.Entry(i.investorName(), i.investedOn(), i.equityPct())));
        if (founders.signum() > 0) {
            entries.add(new MoneyBreakup.Entry(FOUNDERS, LocalDate.EPOCH, founders, MoneyBreakup.GREY));
        }

        int rounds = investments.size();
        List<BreakupSegment> segments = MoneyBreakup.group(entries, EquityChart::pct).stream()
                .map(s -> s.withDetail(detail(s, sold, rounds)))
                .toList();
        return new Result(segments, MoneyBreakup.donut(segments), founders, sold, pct(founders));
    }

    /** The extra tooltip line. Only about equity: when it was bought and how it compares with the rest of what was sold. */
    private static String detail(BreakupSegment s, BigDecimal sold, int rounds) {
        if (FOUNDERS.equals(s.label())) {
            return "Not sold · " + pct(sold) + " sold in " + rounds + (rounds == 1 ? " round" : " rounds");
        }
        String shareOfSold = pct(s.amountInr().multiply(HUNDRED).divide(sold, 1, RoundingMode.HALF_UP)) + " of the equity sold";
        return s.slot() == 0
                ? "Smaller holders together · " + shareOfSold
                : "Since " + s.firstDate().format(DAY) + " · " + shareOfSold;
    }

    /** 10 -> "10%", 7.50 -> "7.5%", 70.5 -> "70.5%". */
    static String pct(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }
}
