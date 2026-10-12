package com.raiseready;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CompanySchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void demoCompanyHasThreeInvestorsAtDistinctTimesValuationsAndEquities() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT investor_name AS investor, invested_on, valuation_inr, amount_inr, equity_pct
                FROM company_investment
                WHERE company_name = 'RaiseReady Pvt Ltd'
                ORDER BY invested_on
                """);

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("investor")).distinct()).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("invested_on")).distinct()).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("valuation_inr")).distinct()).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("equity_pct")).distinct()).hasSize(3);
    }

    @Test
    void storedEquityMatchesAmountOverValuation() {
        Integer inconsistent = jdbc.queryForObject("""
                SELECT count(*) FROM company_investment
                WHERE equity_pct <> round(amount_inr / valuation_inr * 100, 2)
                """, Integer.class);
        assertThat(inconsistent).isZero();
    }
}
