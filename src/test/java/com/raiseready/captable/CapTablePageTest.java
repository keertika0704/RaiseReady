package com.raiseready.captable;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/** The cap table as the investments page shows it, with the A-then-B example in place. Rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CapTablePageTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @BeforeEach
    void investorAThenInvestorB() {
        CapTableFixture.reset(jdbc);
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 1, 10),
                new BigDecimal("2000000"), null, "Investor A", new BigDecimal("8000000")));
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 2, 10),
                new BigDecimal("5000000"), null, "Investor B", new BigDecimal("20000000")));
    }

    @Test
    void investmentsPageShowsEachHoldersSharesAndPercentage() throws Exception {
        mvc.perform(get("/investments"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cap table")))
                .andExpect(content().string(containsString("Investor A")))
                .andExpect(content().string(containsString("2,50,000")))
                .andExpect(content().string(containsString("16.00%")))
                .andExpect(content().string(containsString("Investor B")))
                .andExpect(content().string(containsString("3,12,500")))
                .andExpect(content().string(containsString("20.00%")))
                .andExpect(content().string(containsString("Founder")))
                .andExpect(content().string(containsString("10,00,000")))
                .andExpect(content().string(containsString("64.00%")))
                .andExpect(content().string(containsString("15,62,500")))
                .andExpect(content().string(containsString("Current valuation: ₹2.5 Cr")));
    }

    @Test
    void investorModeSeesTheCapTableToo() throws Exception {
        MockHttpSession investor = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(investor));

        mvc.perform(get("/investments").session(investor))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cap table")))
                .andExpect(content().string(containsString("16.00%")));
    }

    @Test
    void overviewShowsTheCurrentValuationInInvestorMode() throws Exception {
        MockHttpSession investor = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(investor));

        mvc.perform(get("/").session(investor))
                .andExpect(content().string(containsString("Current valuation")))
                .andExpect(content().string(containsString("₹2.5 Cr")));
    }
}
