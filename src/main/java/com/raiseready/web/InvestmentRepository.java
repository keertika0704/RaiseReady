package com.raiseready.web;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvestmentRepository {

    private final JdbcTemplate jdbc;

    public InvestmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Investment> findAllOldestFirst() {
        return jdbc.query("""
                SELECT company_name, investor_name, invested_on, valuation_inr, amount_inr, equity_pct
                FROM company_investment
                ORDER BY invested_on, id
                """,
                (rs, i) -> new Investment(
                        rs.getString("company_name"),
                        rs.getString("investor_name"),
                        rs.getDate("invested_on").toLocalDate(),
                        rs.getBigDecimal("valuation_inr"),
                        rs.getBigDecimal("amount_inr"),
                        rs.getBigDecimal("equity_pct")));
    }
}
