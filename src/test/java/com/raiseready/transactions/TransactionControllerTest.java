package com.raiseready.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Each test is rolled back, so nothing posted here stays in the database. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TransactionRepository repository;

    private static MockHttpSession investorSession(MockMvc mvc) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session));
        return session;
    }

    private static MockHttpServletRequestBuilder validPost() {
        return post("/transactions")
                .param("type", "EXPENSE")
                .param("category", "TRAVEL")
                .param("date", LocalDate.now().toString())
                .param("amount", "2500.50")
                .param("description", "Train tickets");
    }

    @Test
    void investorModeGets403OnEveryTransactionsRouteAndNothingIsSaved() throws Exception {
        MockHttpSession investor = investorSession(mvc);
        int before = repository.findAllNewestFirst().size();

        mvc.perform(get("/transactions").session(investor)).andExpect(status().isForbidden());
        mvc.perform(get("/transactions/new").session(investor)).andExpect(status().isForbidden());
        mvc.perform(validPost().session(investor)).andExpect(status().isForbidden());

        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void founderSavesATransactionThenLandsOnTheListWithItAtTheTop() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(validPost().session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attribute("message", "Transaction added"));

        Transaction saved = repository.findAllNewestFirst().get(0);
        assertThat(saved.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.amountInr()).isEqualByComparingTo("2500.50");
        assertThat(saved.description()).isEqualTo("Train tickets");

        mvc.perform(get("/transactions").session(session).flashAttr("message", "Transaction added"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transaction added")))
                .andExpect(content().string(containsString("Train tickets")))
                .andExpect(content().string(containsString("−₹2,500.50")));
    }

    @Test
    void fundingWithAnInvestorNameIsSavedWithTheNameTidied() throws Exception {
        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "9000000")
                        .param("date", LocalDate.now().toString()).param("amount", "1000000")
                        .param("investorName", "  Rohan   Mehta "))
                .andExpect(redirectedUrl("/transactions"));

        assertThat(repository.findAllNewestFirst().get(0).investorName()).isEqualTo("Rohan Mehta");
    }

    @Test
    void failedChecksShowTheFormAgainWithErrorsAndTheFoundersInputAndSaveNothing() throws Exception {
        int before = repository.findAllNewestFirst().size();

        mvc.perform(post("/transactions")
                        .param("type", "EXPENSE").param("category", "TRAVEL")
                        .param("date", LocalDate.now().plusDays(1).toString())
                        .param("amount", "0")
                        .param("description", "x".repeat(201)))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("The date can&#39;t be in the future.")))
                .andExpect(content().string(containsString("The amount must be more than 0.")))
                .andExpect(content().string(containsString("Keep the description to 200 characters or less.")))
                .andExpect(content().string(containsString("value=\"0\"")));

        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void emptyFormReportsEveryRequiredField() throws Exception {
        mvc.perform(post("/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pick a type.")))
                .andExpect(content().string(containsString("Pick a category.")))
                .andExpect(content().string(containsString("Enter a date.")))
                .andExpect(content().string(containsString("Enter an amount.")));
    }

    @Test
    void businessRuleFailuresComeBackNextToTheirFieldWithInputKept() throws Exception {
        int before = repository.findAllNewestFirst().size();

        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "9000000")
                        .param("date", LocalDate.now().toString()).param("amount", "5000")
                        .param("description", "Seed cheque"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("Enter the investor&#39;s name for an equity investment.")))
                .andExpect(content().string(containsString("Seed cheque")));

        mvc.perform(post("/transactions")
                        .param("type", "EXPENSE").param("category", "SUBSCRIPTION")
                        .param("date", LocalDate.now().toString()).param("amount", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pick a category that belongs to Expense.")));

        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void textInTheAmountBoxGetsAFriendlyMessage() throws Exception {
        mvc.perform(post("/transactions")
                        .param("type", "EXPENSE").param("category", "TRAVEL")
                        .param("date", LocalDate.now().toString()).param("amount", "lots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the amount as a number")));
    }

    @Test
    void theFormDefaultsTheDateToToday() throws Exception {
        mvc.perform(get("/transactions/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + LocalDate.now() + "\"")));
    }

    // ---- editing ----

    private Transaction saved(String type, String category, String amount, String investor) throws Exception {
        var request = post("/transactions").param("type", type).param("category", category)
                .param("date", LocalDate.now().toString()).param("amount", amount).param("description", "Original");
        if (investor != null) {
            request.param("investorName", investor);
        }
        if (category.equals("EQUITY_INVESTMENT")) {
            request.param("preMoneyValuation", "9000000");
        }
        mvc.perform(request).andExpect(redirectedUrl("/transactions"));
        return repository.findAllNewestFirst().get(0);
    }

    @Test
    void listLinksEachRowToItsEditPage() throws Exception {
        Transaction t = saved("EXPENSE", "TRAVEL", "100", null);

        mvc.perform(get("/transactions"))
                .andExpect(content().string(containsString("/transactions/" + t.id() + "/edit")));
    }

    @Test
    void editPageIsFilledInWithTheSavedValuesAndPostsToThatTransaction() throws Exception {
        Transaction t = saved("FUNDING", "EQUITY_INVESTMENT", "1500.75", "Rohan Mehta");

        mvc.perform(get("/transactions/" + t.id() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("Edit transaction")))
                .andExpect(content().string(containsString("action=\"/transactions/" + t.id() + "\"")))
                .andExpect(content().string(containsString("value=\"1500.75\"")))
                .andExpect(content().string(containsString("value=\"Rohan Mehta\"")))
                .andExpect(content().string(containsString("value=\"" + LocalDate.now() + "\"")))
                .andExpect(content().string(containsString("Original")));
    }

    @Test
    void savingAnEditChangesTheRowInPlaceAndShowsUpdated() throws Exception {
        Transaction t = saved("EXPENSE", "TRAVEL", "100", null);
        saved("EXPENSE", "RENT", "999", null);   // a newer row: the edited one must keep its place in the list

        mvc.perform(post("/transactions/" + t.id())
                        .param("type", "EXPENSE").param("category", "MARKETING")
                        .param("date", LocalDate.now().minusDays(2).toString())
                        .param("amount", "250.5").param("description", "Flyers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attribute("message", "Transaction updated"));

        Transaction after = repository.findById(t.id()).orElseThrow();
        assertThat(after.category()).isEqualTo(TransactionCategory.MARKETING);
        assertThat(after.amountInr()).isEqualByComparingTo("250.50");
        assertThat(after.description()).isEqualTo("Flyers");
        assertThat(after.createdAt()).isEqualTo(t.createdAt());
        assertThat(repository.findAllNewestFirst().stream().filter(x -> x.id() == t.id())).hasSize(1);
    }

    @Test
    void aFailedEditShowsTheFormAgainWithErrorsAndInputAndLeavesTheRowAlone() throws Exception {
        Transaction t = saved("EXPENSE", "TRAVEL", "100", null);

        mvc.perform(post("/transactions/" + t.id())
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "9000000")
                        .param("date", LocalDate.now().toString()).param("amount", "777").param("description", "kept me"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("Edit transaction")))
                .andExpect(content().string(containsString("action=\"/transactions/" + t.id() + "\"")))
                .andExpect(content().string(containsString("Enter the investor&#39;s name for an equity investment.")))
                .andExpect(content().string(containsString("kept me")));

        mvc.perform(post("/transactions/" + t.id())
                        .param("type", "EXPENSE").param("category", "TRAVEL")
                        .param("date", LocalDate.now().plusDays(1).toString()).param("amount", "-5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The date can&#39;t be in the future.")))
                .andExpect(content().string(containsString("The amount must be more than 0.")));

        assertThat(repository.findById(t.id()).orElseThrow()).isEqualTo(t);
    }

    @Test
    void editingAnUnknownTransactionIs404() throws Exception {
        long missing = repository.findAllNewestFirst().stream().mapToLong(Transaction::id).max().orElse(0) + 1000;

        mvc.perform(get("/transactions/" + missing + "/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/transactions/" + missing)
                        .param("type", "EXPENSE").param("category", "TRAVEL")
                        .param("date", LocalDate.now().toString()).param("amount", "5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void investorModeCannotOpenOrSaveAnEdit() throws Exception {
        Transaction t = saved("EXPENSE", "TRAVEL", "100", null);
        MockHttpSession investor = investorSession(mvc);

        mvc.perform(get("/transactions/" + t.id() + "/edit").session(investor)).andExpect(status().isForbidden());
        mvc.perform(post("/transactions/" + t.id()).session(investor)
                        .param("type", "EXPENSE").param("category", "MARKETING")
                        .param("date", LocalDate.now().toString()).param("amount", "5"))
                .andExpect(status().isForbidden());

        assertThat(repository.findById(t.id()).orElseThrow()).isEqualTo(t);
    }

    // ---- equity and valuation ----

    @Test
    void equityInvestmentWithoutPreMoneyComesBackWithAnErrorNextToThePreMoneyField() throws Exception {
        int before = repository.findAllNewestFirst().size();

        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT")
                        .param("date", LocalDate.now().toString()).param("amount", "5000").param("investorName", "Ananya Rao"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("Enter the pre-money valuation for an equity investment.")))
                .andExpect(content().string(containsString("value=\"Ananya Rao\"")));

        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "0")
                        .param("date", LocalDate.now().toString()).param("amount", "5000").param("investorName", "Ananya Rao"))
                .andExpect(content().string(containsString("The pre-money valuation must be more than 0.")));

        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "lots")
                        .param("date", LocalDate.now().toString()).param("amount", "5000").param("investorName", "Ananya Rao"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"));

        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void listShowsEquitySharesAndPostMoneyOnEquityRowsOnly() throws Exception {
        mvc.perform(post("/transactions")
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "18000000")
                        .param("date", LocalDate.now().toString()).param("amount", "2000000").param("investorName", "Ananya Rao"))
                .andExpect(redirectedUrl("/transactions"));
        Transaction equity = repository.findAllNewestFirst().get(0);
        assertThat(equity.postMoneyValuationInr()).isEqualByComparingTo("20000000.00");
        assertThat(equity.sharesIssued()).isPositive();

        mvc.perform(get("/transactions"))
                .andExpect(content().string(containsString("10.00% equity · post-money valuation ₹2 Cr")))
                .andExpect(content().string(containsString(" new shares")));
    }

    @Test
    void editPageIsFilledInWithThePreMoneyAndSaysTheMoneyTermsAreLocked() throws Exception {
        Transaction t = saved("FUNDING", "EQUITY_INVESTMENT", "2000000", "Ananya Rao");

        mvc.perform(get("/transactions/" + t.id() + "/edit"))
                .andExpect(content().string(containsString("value=\"9000000.00\"")))
                .andExpect(content().string(containsString("only the date and description can be changed")));
    }

    @Test
    void editingTheAmountOfAnInvestmentThatCreatedSharesShowsAnErrorAndSavesNothing() throws Exception {
        Transaction t = saved("FUNDING", "EQUITY_INVESTMENT", "2000000", "Ananya Rao");

        mvc.perform(post("/transactions/" + t.id())
                        .param("type", "FUNDING").param("category", "EQUITY_INVESTMENT").param("preMoneyValuation", "9000000")
                        .param("date", LocalDate.now().toString()).param("amount", "3000000").param("investorName", "Ananya Rao"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-form"))
                .andExpect(content().string(containsString("Shares were issued for this investment")));

        assertThat(repository.findById(t.id()).orElseThrow()).isEqualTo(t);
    }
}
