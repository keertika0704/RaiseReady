package com.raiseready.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Groups amounts by label and works out each group's share and colour, for the dashboard's donut charts. */
final class MoneyBreakup {

    /** The palette has eight categorical hues. A ninth is never invented: everything past the eighth folds into a grey "Other". */
    static final int MAX_COLOURS = 8;

    private MoneyBreakup() {
    }

    /** Give an entry this slot to draw it in grey, last, outside the eight colours (the founders' own equity). */
    static final int GREY = -1;

    /**
     * One piece to be grouped. {@code slot} fixes the colour of its label (1 to 8); 0 means "give it the next
     * free colour in order of first appearance"; {@link #GREY} means grey.
     */
    record Entry(String label, LocalDate date, BigDecimal amount, int slot) {
        Entry(String label, LocalDate date, BigDecimal amount) {
            this(label, date, amount, 0);
        }
    }

    static List<BreakupSegment> group(List<Entry> entries) {
        return group(entries, BigDecimal::toPlainString);
    }

    /**
     * One segment per label, in the order the labels first appear. A label with no fixed slot is coloured by that
     * order, so colours stay put as new entries are added later in time. Grey labels go last and never count towards
     * the eight colours. Empty when there is nothing to show.
     *
     * @param valueText turns an amount into the text shown for it (₹20 lakh, 10%)
     */
    static List<BreakupSegment> group(List<Entry> entries, Function<BigDecimal, String> valueText) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, LocalDate> firstDates = new LinkedHashMap<>();
        Map<String, Integer> fixedSlots = new LinkedHashMap<>();
        for (Entry entry : entries) {
            amounts.merge(entry.label(), entry.amount(), BigDecimal::add);
            firstDates.merge(entry.label(), entry.date(), (a, b) -> a.isBefore(b) ? a : b);
            if (entry.slot() != 0) {
                fixedSlots.put(entry.label(), entry.slot());
            }
        }
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return List.of();
        }

        List<String> coloured = new ArrayList<>();
        List<String> grey = new ArrayList<>();
        for (String label : amounts.keySet()) {
            (fixedSlots.getOrDefault(label, 0) == GREY ? grey : coloured).add(label);
        }

        List<BreakupSegment> segments = new ArrayList<>();
        boolean fold = coloured.size() > MAX_COLOURS;
        int named = fold ? MAX_COLOURS : coloured.size();
        for (int i = 0; i < named; i++) {
            String label = coloured.get(i);
            segments.add(segment(label, firstDates.get(label), amounts.get(label), total, fixedSlots.getOrDefault(label, i + 1), valueText));
        }
        if (fold) {
            List<String> rest = coloured.subList(named, coloured.size());
            BigDecimal restAmount = rest.stream().map(amounts::get).reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate restFirst = rest.stream().map(firstDates::get).min(LocalDate::compareTo).orElseThrow();
            segments.add(segment("Other (" + rest.size() + " more)", restFirst, restAmount, total, 0, valueText));
        }
        for (String label : grey) {
            segments.add(segment(label, firstDates.get(label), amounts.get(label), total, 0, valueText));
        }
        return segments;
    }

    private static BreakupSegment segment(String label, LocalDate first, BigDecimal amount, BigDecimal total, int slot,
                                          Function<BigDecimal, String> valueText) {
        return new BreakupSegment(label, first, amount, share(amount, total), slot, valueText.apply(amount), null);
    }

    /** Donut geometry in a 200 x 200 viewBox: ring radius 66, centred on (100, 100), starting at 12 o'clock. */
    private static final double CENTRE = 100;
    private static final double RADIUS = 66;
    /** The 2px surface gap between arcs, as a percent of the ring's circumference. */
    private static final double GAP = 2 / (2 * Math.PI * RADIUS) * 100;
    /** Arcs shorter than this (percent of the ring) are too short for a label inside them. */
    private static final double MIN_LABEL_PERCENT = 10;

    static List<DonutSlice> donut(List<BreakupSegment> segments) {
        BigDecimal total = segments.stream().map(BreakupSegment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        double gap = segments.size() > 1 ? GAP : 0;
        List<DonutSlice> slices = new ArrayList<>();
        double start = 0;
        for (BreakupSegment s : segments) {
            double percent = s.amountInr().multiply(BigDecimal.valueOf(100)).divide(total, 6, RoundingMode.HALF_UP).doubleValue();
            double angle = (start + percent / 2) / 100 * 2 * Math.PI - Math.PI / 2;
            double dash = Math.max(percent - gap, 0.2);
            slices.add(new DonutSlice(s.slot(), s.label(), s.amountInr(), s.sharePct(), s.valueText(), s.detail(),
                    num(dash) + " " + num(100 - dash), num(-start),
                    num(CENTRE + RADIUS * Math.cos(angle)), num(CENTRE + RADIUS * Math.sin(angle)),
                    percent >= MIN_LABEL_PERCENT));
            start += percent;
        }
        return slices;
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal total) {
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
