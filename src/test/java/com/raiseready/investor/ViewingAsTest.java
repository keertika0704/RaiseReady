package com.raiseready.investor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

import com.raiseready.captable.CapTableFixture;
import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/** The "Viewing as" dropdown and the investor's three numbers, with the A-then-B example in place. Rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ViewingAsTest {

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

    private MockHttpSession investorSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session));
        return session;
    }

    @Test
    void theDropdownIsInInvestorModeOnEveryPageAndListsInvestorsButNotTheFounder() throws Exception {
        MockHttpSession investor = investorSession();

        for (String page : new String[] {"/", "/investments"}) {
            mvc.perform(get(page).session(investor))
                    .andExpect(content().string(containsString("Viewing as</label>")))
                    .andExpect(content().string(containsString("<option value=\"Investor A\" selected=\"selected\">Investor A</option>")))
                    .andExpect(content().string(containsString("<option value=\"Investor B\">Investor B</option>")))
                    .andExpect(content().string(not(containsString("<option value=\"Founder\""))));
        }
    }

    @Test
    void thereIsNoDropdownInFounderMode() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(not(containsString("Viewing as"))))
                .andExpect(content().string(not(containsString("viewing-as"))));
    }

    @Test
    void investorModeShowsTotalInvestedEquityAndCurrentValueOfTheFirstInvestorByDefault() throws Exception {
        mvc.perform(get("/").session(investorSession()))
                .andExpect(content().string(containsString("Viewing as Investor A")))
                .andExpect(content().string(containsString("<span>Total invested</span><strong>₹20 lakh</strong>")))
                .andExpect(content().string(containsString("<span>Equity</span><strong>16.00%</strong>")))
                .andExpect(content().string(containsString("<span>Current value</span><strong>₹40 lakh</strong>")));
    }

    @Test
    void choosingAnotherInvestorSwitchesTheNumbersAndIsRememberedInTheSession() throws Exception {
        MockHttpSession investor = investorSession();

        mvc.perform(post("/viewing-as").param("investor", "Investor B").session(investor).header("Referer", "http://localhost/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        for (String page : new String[] {"/", "/investments"}) {
            mvc.perform(get(page).session(investor))
                    .andExpect(content().string(containsString("<option value=\"Investor B\" selected=\"selected\">Investor B</option>")));
        }
        mvc.perform(get("/").session(investor))
                .andExpect(content().string(containsString("Viewing as Investor B")))
                .andExpect(content().string(containsString("<span>Total invested</span><strong>₹50 lakh</strong>")))
                .andExpect(content().string(containsString("<span>Equity</span><strong>20.00%</strong>")))
                .andExpect(content().string(containsString("<span>Current value</span><strong>₹50 lakh</strong>")));
    }

    @Test
    void anotherSessionStillStartsOnTheFirstInvestor() throws Exception {
        mvc.perform(post("/viewing-as").param("investor", "Investor B").session(investorSession()));

        mvc.perform(get("/").session(investorSession()))
                .andExpect(content().string(containsString("Viewing as Investor A")));
    }

    @Test
    void theFounderAndUnknownNamesCannotBeChosen() throws Exception {
        MockHttpSession investor = investorSession();

        mvc.perform(post("/viewing-as").param("investor", "Founder").session(investor)).andExpect(status().isBadRequest());
        mvc.perform(post("/viewing-as").param("investor", "Nobody").session(investor)).andExpect(status().isBadRequest());

        mvc.perform(get("/").session(investor)).andExpect(content().string(containsString("Viewing as Investor A")));
    }

    @Test
    void founderModeIsUnchangedByTheChoice() throws Exception {
        MockHttpSession session = investorSession();
        mvc.perform(post("/viewing-as").param("investor", "Investor B").session(session));
        mvc.perform(post("/mode").param("mode", "FOUNDER").session(session));

        mvc.perform(get("/").session(session))
                .andExpect(content().string(not(containsString("Total invested"))))
                .andExpect(content().string(containsString("Total raised")));
    }
}
