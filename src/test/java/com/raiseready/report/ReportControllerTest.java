package com.raiseready.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.raiseready.captable.CapTableSnapshot;
import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/**
 * The report PDF against the pages a user already trusts. Each figure is read off the Overview or Investments page as the
 * browser would see it, then looked for in the PDF's own text. Real database, rolled back, with the A-then-B example plus
 * some spending and subscriptions so the metrics are not all zero.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    private static final Pattern CARD = Pattern.compile("<div class=\"card\"><span>([^<]+)</span><strong>([^<]+)</strong>");
    private static final Pattern HOLDER_ROW = Pattern.compile("<tr>\\s*<td>([^<]+)</td>\\s*<td class=\"num\">([^<]+)</td>\\s*<td class=\"num\">([^<]+)</td>\\s*</tr>");
    private static final Pattern TOTAL_ROW = Pattern.compile("<th>Total</th><th class=\"num\">([^<]+)</th>");
    private static final Pattern VALUATION = Pattern.compile("Current valuation: ([^ ]+ ?[A-Za-z]*) \\(");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @BeforeEach
    void aCompanyWithRoundsSpendingAndSubscriptions() {
        CapTableFixture.reset(jdbc);
        LocalDate today = LocalDate.now();
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, today.minusDays(60),
                new BigDecimal("2000000"), null, "Investor A", new BigDecimal("8000000")));
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, today.minusDays(40),
                new BigDecimal("5000000"), null, "Investor B", new BigDecimal("20000000")));
        transactions.add(new NewTransaction(TransactionType.EXPENSE, TransactionCategory.SALARIES, today.minusDays(3),
                new BigDecimal("640000"), null, null, null));
        transactions.add(new NewTransaction(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, today.minusDays(2),
                new BigDecimal("45000"), null, null, null));
        transactions.add(new NewTransaction(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, today.minusDays(1),
                new BigDecimal("30000"), null, null, null));
    }

    private static MockHttpSession investorSession(MockMvc mvc) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session));
        return session;
    }

    private String html(String path, MockHttpSession session) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String pdfText(MockHttpSession session) throws Exception {
        byte[] pdf = mvc.perform(get("/report.pdf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return PdfText.of(pdf);
    }

    /** The label/value cards on a page, in order, e.g. {"Cash", "₹5.89 lakh"}. */
    private static List<String[]> cards(String html) {
        List<String[]> found = new ArrayList<>();
        Matcher m = CARD.matcher(html);
        while (m.find()) {
            found.add(new String[] {m.group(1), m.group(2)});
        }
        return found;
    }

    private static String cardValue(String html, String label) {
        return cards(html).stream().filter(c -> c[0].equals(label)).map(c -> c[1]).findFirst()
                .orElseThrow(() -> new AssertionError("No \"" + label + "\" card on the page"));
    }

    /** Every holder row and the total on the Investments page appear in the PDF the same way. */
    private void assertCapTableMatches(String investmentsHtml, String pdf) {
        int holders = 0;
        Matcher m = HOLDER_ROW.matcher(investmentsHtml.substring(investmentsHtml.indexOf("class=\"cap-table\"")));
        while (m.find()) {
            assertThat(pdf).contains(PdfText.s(m.group(1) + " " + m.group(2) + " " + m.group(3)));
            holders++;
        }
        assertThat(holders).as("holders read off the Investments page").isEqualTo(3);
        Matcher total = TOTAL_ROW.matcher(investmentsHtml);
        assertThat(total.find()).isTrue();
        assertThat(pdf).contains(PdfText.s("Total " + total.group(1) + " 100%"));
    }

    @Test
    void founderPdfNumbersMatchTheDashboard() throws Exception {
        MockHttpSession founder = new MockHttpSession();
        String overview = html("/", founder);
        String investments = html("/investments", founder);
        String pdf = pdfText(founder);

        // The four metrics, as the dashboard prints them.
        for (String label : List.of("Cash", "Monthly burn", "Runway", "MRR")) {
            assertThat(pdf).as(label).contains(PdfText.s(label + " " + cardValue(overview, label)));
        }
        assertThat(cardValue(overview, "Cash")).startsWith("₹");
        assertThat(pdf).contains(PdfText.s(cardValue(overview, "Active subscriptions") + " active subscriptions"));

        // The valuation as the Investments page prints it, and the cap table row by row.
        Matcher valuation = VALUATION.matcher(investments);
        assertThat(valuation.find()).isTrue();
        assertThat(valuation.group(1)).isEqualTo("₹2.5 Cr");
        assertThat(pdf).contains(PdfText.s("Current valuation " + valuation.group(1)));
        assertCapTableMatches(investments, pdf);
    }

    @Test
    void investorPdfNumbersMatchTheDashboardForEachInvestor() throws Exception {
        for (String name : List.of("Investor A", "Investor B")) {
            MockHttpSession investor = investorSession(mvc);
            mvc.perform(post("/viewing-as").param("investor", name).session(investor));
            String overview = html("/", investor);
            String investments = html("/investments", investor);
            String pdf = pdfText(investor);

            assertThat(pdf).contains(PdfText.s("Viewing as " + name));
            for (String label : List.of("Total invested", "Equity", "Current value", "Current valuation", "Funding rounds")) {
                assertThat(pdf).as(name + " " + label).contains(PdfText.s(label + " " + cardValue(overview, label)));
            }
            assertThat(cardValue(overview, "Current value")).startsWith("₹");
            assertCapTableMatches(investments, pdf);
        }
    }

    @Test
    void investorsNeverSeeTheCompanysOwnFinancialsInTheReportOrThePdf() throws Exception {
        MockHttpSession investor = investorSession(mvc);

        String page = html("/report", investor);
        String pdf = pdfText(investor);

        for (String founderOnly : List.of("Monthly burn", "Runway", "MRR", "Key metrics", "Cash")) {
            assertThat(page).as("page " + founderOnly).doesNotContain(founderOnly);
            assertThat(pdf).as("pdf " + founderOnly).doesNotContain(PdfText.s(founderOnly));
        }
        assertThat(html("/", investor)).doesNotContain("Monthly burn");
    }

    @Test
    void reportPageShowsTheSameFiguresAsThePdfInBothModes() throws Exception {
        MockHttpSession founder = new MockHttpSession();
        MockHttpSession investor = investorSession(mvc);

        for (MockHttpSession session : List.of(founder, investor)) {
            String page = html("/report", session);
            String pdf = pdfText(session);
            for (String[] card : cards(page)) {
                assertThat(pdf).as(card[0]).contains(PdfText.s(card[0] + " " + card[1]));
            }
            assertThat(cards(page)).isNotEmpty();
            assertCapTableMatches(html("/investments", session), pdf);
        }
        assertThat(cards(html("/report", founder))).extracting(c -> c[0]).contains("Cash", "Monthly burn", "Runway", "MRR", "Current valuation");
    }

    @Test
    void bothModesGetADownloadPdfButtonAndAPdfBack() throws Exception {
        for (MockHttpSession session : List.of(new MockHttpSession(), investorSession(mvc))) {
            mvc.perform(get("/report").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Download PDF")))
                    .andExpect(content().string(containsString("href=\"/report.pdf\"")));
            mvc.perform(get("/report.pdf").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition", containsString(".pdf")))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/report.pdf")).andExpect(header().string("Content-Disposition", containsString("founder-report")));
        mvc.perform(get("/report.pdf").session(investorSession(mvc))).andExpect(header().string("Content-Disposition", containsString("investor-report")));
    }

    @Test
    void theReportIsInTheNavigationInBothModes() throws Exception {
        mvc.perform(get("/investments")).andExpect(content().string(containsString("href=\"/report\"")));
        mvc.perform(get("/investments").session(investorSession(mvc))).andExpect(content().string(containsString("href=\"/report\"")));
    }

    @Test
    void makingTheReportChangesNothing() throws Exception {
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);

        pdfText(new MockHttpSession());
        pdfText(investorSession(mvc));
        html("/report", new MockHttpSession());

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
    }

    @Test
    void thePdfIsAReadableDocumentNotEmpty() throws IOException, Exception {
        String pdf = pdfText(new MockHttpSession());

        assertThat(pdf).contains(PdfText.s("Founder report", "Key metrics", "Valuation", "Cap table"));
        assertThat(pdf).doesNotContain(PdfText.s("null"));
        mvc.perform(get("/report.pdf")).andExpect(content().string(not(containsString("Exception"))));
    }
}
