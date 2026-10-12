package com.raiseready.captable;

/** A row of the shareholder table: one holder and how many shares they own. */
public record Shareholder(long id, String name, long shares) {
}
