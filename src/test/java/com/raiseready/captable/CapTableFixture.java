package com.raiseready.captable;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Puts the cap table back to its starting point (the founder holding every share, no equity rounds, no valuation)
 * so a test can work out exact numbers whatever rows the dev database already has. Call it from inside a
 * {@code @Transactional} test: it is rolled back with the rest.
 */
public final class CapTableFixture {

    private CapTableFixture() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM transactions WHERE category = 'EQUITY_INVESTMENT'");
        jdbc.update("DELETE FROM shareholder WHERE name <> 'Founder'");
        jdbc.update("UPDATE company SET current_valuation_inr = NULL");
    }
}
