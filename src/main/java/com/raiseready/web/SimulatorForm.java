package com.raiseready.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** The simulator's two inputs as the browser sends them. Basic checks only; the cap table rules are in CapTableService. */
public class SimulatorForm {

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.00", inclusive = false, message = "The amount must be more than 0.")
    @DecimalMax(value = "9999999999999.99", message = "That amount is too large.")
    private BigDecimal amount;

    @NotNull(message = "Enter the pre-money valuation.")
    @DecimalMin(value = "0.00", inclusive = false, message = "The pre-money valuation must be more than 0.")
    @DecimalMax(value = "9999999999999.99", message = "That valuation is too large.")
    private BigDecimal preMoneyValuation;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPreMoneyValuation() { return preMoneyValuation; }
    public void setPreMoneyValuation(BigDecimal preMoneyValuation) { this.preMoneyValuation = preMoneyValuation; }
}
