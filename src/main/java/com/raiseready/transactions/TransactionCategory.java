package com.raiseready.transactions;

import static com.raiseready.transactions.TransactionType.EXPENSE;
import static com.raiseready.transactions.TransactionType.FUNDING;
import static com.raiseready.transactions.TransactionType.REVENUE;

/** Every category belongs to exactly one type. {@code recurring} says whether it repeats (salaries, a subscription). */
public enum TransactionCategory {
    SALARIES(EXPENSE, "Salaries", true),
    RENT(EXPENSE, "Rent", true),
    SOFTWARE(EXPENSE, "Software & tools", true),
    MARKETING(EXPENSE, "Marketing", false),
    TRAVEL(EXPENSE, "Travel", false),
    LEGAL(EXPENSE, "Legal & compliance", false),
    OTHER_EXPENSE(EXPENSE, "Other expense", false),

    SUBSCRIPTION(REVENUE, "Subscription", true),
    ONE_TIME_SALE(REVENUE, "One-time sale", false),
    SERVICES(REVENUE, "Services", false),
    OTHER_REVENUE(REVENUE, "Other revenue", false),

    EQUITY_INVESTMENT(FUNDING, "Equity investment", false),
    LOAN(FUNDING, "Loan", false),
    GRANT(FUNDING, "Grant", false);

    private final TransactionType type;
    private final String label;
    private final boolean recurring;

    TransactionCategory(TransactionType type, String label, boolean recurring) {
        this.type = type;
        this.label = label;
        this.recurring = recurring;
    }

    public TransactionType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRecurring() {
        return recurring;
    }
}
