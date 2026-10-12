package com.raiseready.web;

import java.math.BigDecimal;

/**
 * One arc of the donut, already worked out for SVG. Each arc is a circle outline drawn with {@code pathLength="100"},
 * so {@code dash} is the arc's length in percent of the ring and {@code offset} is where it starts (negative, as SVG wants).
 * The label sits on the middle of the arc; it is only shown when the arc is long enough to hold it.
 */
public record DonutSlice(int slot, String label, BigDecimal amountInr, BigDecimal sharePct, String valueText, String detail,
                         String dash, String offset, String labelX, String labelY, boolean showLabel) {
}
