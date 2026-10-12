package com.raiseready.captable;

/** An equity investment that cannot be put on the cap table. {@code field} names the form field the message belongs next to. */
public class CapTableRuleException extends RuntimeException {

    private final String field;

    public CapTableRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
