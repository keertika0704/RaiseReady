package com.raiseready.captable;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The one row of the company table. */
@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbc;

    public CompanyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Null until the first equity investment has been saved. */
    public BigDecimal currentValuation() {
        return jdbc.queryForObject("SELECT current_valuation_inr FROM company WHERE id = 1", BigDecimal.class);
    }

    /**
     * Sets the current valuation to the post-money of the latest equity investment in the ledger (by date, then by
     * when it was saved). Left as it was if there is none. Call it after the investment's row is saved.
     */
    public void refreshCurrentValuation() {
        jdbc.update("""
                UPDATE company
                SET current_valuation_inr = COALESCE(
                    (SELECT pre_money_valuation_inr + amount_inr
                     FROM transactions
                     WHERE shares_issued IS NOT NULL
                     ORDER BY occurred_on DESC, id DESC
                     LIMIT 1),
                    current_valuation_inr)
                WHERE id = 1
                """);
    }
}
