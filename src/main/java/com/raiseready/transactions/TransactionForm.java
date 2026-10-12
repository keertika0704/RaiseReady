package com.raiseready.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** The add-transaction form as the browser posts it. Basic checks only; the business rules are in TransactionService. */
public class TransactionForm {

    @NotNull(message = "Pick a type.")
    private TransactionType type;

    @NotNull(message = "Pick a category.")
    private TransactionCategory category;

    @NotNull(message = "Enter a date.")
    @PastOrPresent(message = "The date can't be in the future.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.00", inclusive = false, message = "The amount must be more than 0.")
    @DecimalMax(value = "9999999999999.99", message = "That amount is too large.")
    private BigDecimal amount;

    @Size(max = 200, message = "Keep the description to 200 characters or less.")
    private String description;

    @Size(max = 200, message = "Keep the investor name to 200 characters or less.")
    private String investorName;

    /** A form filled in with what is saved, for editing. */
    public static TransactionForm from(Transaction t) {
        TransactionForm form = new TransactionForm();
        form.type = t.type();
        form.category = t.category();
        form.date = t.occurredOn();
        form.amount = t.amountInr();
        form.description = t.description();
        form.investorName = t.investorName();
        form.preMoneyValuation = t.preMoneyValuationInr();
        return form;
    }

    /** What the company was worth before this investment. Only used by an equity investment. */
    @DecimalMin(value = "0.00", inclusive = false, message = "The pre-money valuation must be more than 0.")
    @DecimalMax(value = "9999999999999.99", message = "That valuation is too large.")
    private BigDecimal preMoneyValuation;

    public NewTransaction toRequest() {
        return new NewTransaction(type, category, date, amount, description, investorName, preMoneyValuation);
    }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public TransactionCategory getCategory() { return category; }
    public void setCategory(TransactionCategory category) { this.category = category; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPreMoneyValuation() { return preMoneyValuation; }
    public void setPreMoneyValuation(BigDecimal preMoneyValuation) { this.preMoneyValuation = preMoneyValuation; }
    public String getInvestorName() { return investorName; }
    public void setInvestorName(String investorName) { this.investorName = investorName; }
}
