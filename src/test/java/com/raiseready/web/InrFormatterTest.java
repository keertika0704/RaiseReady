package com.raiseready.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class InrFormatterTest {

    private final InrFormatter inr = new InrFormatter();

    @Test
    void exactUsesIndianGroupingAndPaise() {
        assertEquals("₹0.00", inr.exact(BigDecimal.ZERO));
        assertEquals("₹999.00", inr.exact(new BigDecimal("999")));
        assertEquals("₹1,000.00", inr.exact(new BigDecimal("1000")));
        assertEquals("₹2,500.50", inr.exact(new BigDecimal("2500.5")));
        assertEquals("₹1,00,000.00", inr.exact(new BigDecimal("100000")));
        assertEquals("₹12,34,567.89", inr.exact(new BigDecimal("1234567.89")));
        assertEquals("₹1,23,45,678.00", inr.exact(new BigDecimal("12345678")));
        assertEquals("-₹1,500.00", inr.exact(new BigDecimal("-1500")));
    }

    @Test
    void shortFormHandlesNegativeAmounts() {
        assertEquals("-₹96 lakh", inr.format(new BigDecimal("-9600000")));
        assertEquals("₹0", inr.format(BigDecimal.ZERO));
        assertEquals("₹999", inr.format(new BigDecimal("999")));
        assertEquals("₹50,000", inr.format(new BigDecimal("50000")));
        assertEquals("₹99,999", inr.format(new BigDecimal("99999")));
        assertEquals("-₹8,000", inr.format(new BigDecimal("-8000")));
        assertEquals("₹1 lakh", inr.format(new BigDecimal("100000")));
    }

    @Test
    void sharesAreGroupedTheIndianWay() {
        assertEquals("0", inr.shares(0));
        assertEquals("999", inr.shares(999));
        assertEquals("2,50,000", inr.shares(250_000));
        assertEquals("10,00,000", inr.shares(1_000_000));
        assertEquals("15,62,500", inr.shares(1_562_500));
    }
}
