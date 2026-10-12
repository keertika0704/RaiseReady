package com.raiseready.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/** Formats rupee amounts the way they are written in India: ₹2 Cr, ₹96 lakh. Used in templates as ${@inr.format(x)}. */
@Component("inr")
public class InrFormatter {

    private static final BigDecimal CRORE = new BigDecimal("10000000");
    private static final BigDecimal LAKH = new BigDecimal("100000");

    public String format(BigDecimal rupees) {
        if (rupees.signum() < 0) {
            return "-" + format(rupees.negate());
        }
        if (rupees.compareTo(CRORE) >= 0) {
            return "₹" + trim(rupees.divide(CRORE, 2, RoundingMode.HALF_UP)) + " Cr";
        }
        if (rupees.compareTo(LAKH) >= 0) {
            return "₹" + trim(rupees.divide(LAKH, 2, RoundingMode.HALF_UP)) + " lakh";
        }
        return "₹" + group(rupees.setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

    /** The full amount with Indian digit grouping and paise: ₹12,34,567.50. Used where the exact figure matters. */
    public String exact(BigDecimal rupees) {
        BigDecimal rounded = rupees.setScale(2, RoundingMode.HALF_UP);
        String[] parts = rounded.abs().toPlainString().split("\\.");
        return (rounded.signum() < 0 ? "-" : "") + "₹" + group(parts[0]) + "." + parts[1];
    }

    /** A whole number of shares with Indian digit grouping: 1250000 -> 12,50,000. */
    public String shares(long count) {
        return group(Long.toString(count));
    }

    /** Indian digit grouping for a whole number written as digits: the last three together, then pairs (12,34,567). */
    private static String group(String whole) {
        if (whole.length() <= 3) {
            return whole;
        }
        String rest = whole.substring(0, whole.length() - 3);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) {
                grouped.append(',');
            }
            grouped.append(rest.charAt(i));
        }
        return grouped.append(',').append(whole.substring(whole.length() - 3)).toString();
    }

    private static String trim(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }
}
