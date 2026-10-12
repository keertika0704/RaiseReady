package com.raiseready.transactions;

import java.util.Map;

/** One or more business rules failed. Keyed by the form field each message belongs next to. */
public class TransactionRuleException extends RuntimeException {

    private final Map<String, String> errors;

    public TransactionRuleException(Map<String, String> errors) {
        super("Transaction breaks business rules: " + errors);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
