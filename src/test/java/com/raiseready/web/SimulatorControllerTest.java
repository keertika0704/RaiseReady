package com.raiseready.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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

import com.raiseready.captable.CapTable;
import com.raiseready.captable.CapTableFixture;
import com.raiseready.captable.CapTableService;
import com.raiseready.captable.CapTableSnapshot;
import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/** The founder's simulator page, with the A-then-B example in place. Rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SimulatorControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @Autowired
    CapTableService capTable;

    @BeforeEach
    void investorAThenInvestorB() {
        CapTableFixture.reset(jdbc);
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 1, 10),
                new BigDecimal("2000000"), null, "Investor A", new BigDecimal("8000000")));
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 2, 10),
                new BigDecimal("5000000"), null, "Investor B", new BigDecimal("20000000")));
    }

    private static MockHttpSession investorSession(MockMvc mvc) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session));
        return session;
    }

    @Test
    void founderSeesTheFormWithoutAResult() throws Exception {
        mvc.perform(get("/simulator"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Round simulator")))
                .andExpect(content().string(containsString("Nothing here is saved")))
                .andExpect(content().string(not(containsString("If this round happened"))));
    }

    @Test
    void founderSeesTheNewPercentagesForAPretendRound() throws Exception {
        mvc.perform(get("/simulator").param("amount", "5000000").param("preMoneyValuation", "25000000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("If this round happened")))
                .andExpect(content().string(containsString("Post-money valuation: ₹3 Cr")))
                .andExpect(content().string(containsString("16.67%")))
                .andExpect(content().string(containsString("3,12,500")))
                .andExpect(content().string(containsString("New investor (pretend)")))
                .andExpect(content().string(containsString("53.33%")))
                .andExpect(content().string(containsString("13.33%")))
                .andExpect(content().string(containsString("18,75,000")))
                // What each holder owns now is shown beside it.
                .andExpect(content().string(containsString("64.00%")))
                .andExpect(content().string(containsString("16.00%")));
    }

    @Test
    void theRealCapTableAndInvestmentsPageAreTheSameAfterASimulation() throws Exception {
        CapTable tableBefore = capTable.current();
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);
        String pageBefore = mvc.perform(get("/investments")).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/simulator").param("amount", "5000000").param("preMoneyValuation", "25000000")).andExpect(status().isOk());

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
        assertThat(capTable.current()).isEqualTo(tableBefore);
        assertThat(mvc.perform(get("/investments")).andReturn().getResponse().getContentAsString()).isEqualTo(pageBefore);
        assertThat(pageBefore).doesNotContain("New investor");
    }

    @Test
    void badInputShowsErrorsAndSimulatesNothing() throws Exception {
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);

        mvc.perform(get("/simulator").param("amount", "").param("preMoneyValuation", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter an amount.")))
                .andExpect(content().string(containsString("Enter the pre-money valuation.")))
                .andExpect(content().string(not(containsString("If this round happened"))));
        mvc.perform(get("/simulator").param("amount", "-5").param("preMoneyValuation", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The amount must be more than 0.")))
                .andExpect(model().attributeHasFieldErrors("form", "preMoneyValuation"))
                .andExpect(content().string(not(containsString("If this round happened"))));

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
    }

    @Test
    void aRoundTheCapTableWouldRefuseIsRefusedHereToo() throws Exception {
        mvc.perform(get("/simulator").param("amount", "1").param("preMoneyValuation", "999999999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That is too little to buy a share")))
                .andExpect(content().string(not(containsString("If this round happened"))));
    }

    @Test
    void investorModeGets403AndSimulatesNothing() throws Exception {
        MockHttpSession investor = investorSession(mvc);

        mvc.perform(get("/simulator").session(investor)).andExpect(status().isForbidden());
        mvc.perform(get("/simulator").param("amount", "5000000").param("preMoneyValuation", "25000000").session(investor))
                .andExpect(status().isForbidden());
    }

    @Test
    void thereIsNoWayToPostASimulationSoNothingCanBeSavedThatWay() throws Exception {
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);

        mvc.perform(post("/simulator").param("amount", "5000000").param("preMoneyValuation", "25000000"))
                .andExpect(status().isMethodNotAllowed());

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
    }

    @Test
    void founderSeesTheSimulatorInTheNavigationAndInvestorDoesNot() throws Exception {
        mvc.perform(get("/investments")).andExpect(content().string(containsString("href=\"/simulator\"")));
        mvc.perform(get("/investments").session(investorSession(mvc))).andExpect(content().string(not(containsString("/simulator"))));
    }
}
