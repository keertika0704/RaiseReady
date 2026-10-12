package com.raiseready.captable;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Everything the cap table lives in, as it is in the database right now: every shareholder row, every transaction row and
 * the company row. Take one before and one after to prove an action saved nothing. Two snapshots are equal only if every
 * row is identical, so a changed share count, an added row or a new valuation each make them differ.
 */
public record CapTableSnapshot(List<Map<String, Object>> shareholders, List<Map<String, Object>> transactions, List<Map<String, Object>> company) {

    public static CapTableSnapshot take(JdbcTemplate jdbc) {
        return new CapTableSnapshot(
                jdbc.queryForList("SELECT * FROM shareholder ORDER BY id"),
                jdbc.queryForList("SELECT * FROM transactions ORDER BY id"),
                jdbc.queryForList("SELECT * FROM company ORDER BY id"));
    }
}
