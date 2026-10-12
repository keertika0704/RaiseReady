package com.raiseready.transactions;

public enum TransactionType {
    EXPENSE("Expense"),
    REVENUE("Revenue"),
    FUNDING("Funding");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
