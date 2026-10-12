package com.raiseready.captable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.Transaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionRuleException;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/** Real database, rolled back. Starts each test from the founder holding all 10,00,000 shares. */
@SpringBootTest
@Transactional
class CapTableTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @Autowired
    CapTableService capTable;

    @BeforeEach
    void startFromTheFounderHoldingEverything() {
        CapTableFixture.reset(jdbc);
    }

    private Transaction invest(String investor, String preMoney, String amount, LocalDate on) {
        return transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, on,
                new BigDecimal(amount), null, investor, new BigDecimal(preMoney)));
    }

    private static CapTable.Holding holding(CapTable table, String name) {
        return table.holdings().stream().filter(h -> h.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theFounderStartsWithAllTheShares() {
        CapTable table = capTable.current();

        assertThat(table.holdings()).hasSize(1);
        assertThat(table.holdings().get(0).name()).isEqualTo("Founder");
        assertThat(table.holdings().get(0).shares()).isEqualTo(1_000_000);
        assertThat(table.holdings().get(0).pct()).isEqualByComparingTo("100");
        assertThat(table.totalShares()).isEqualTo(1_000_000);
        assertThat(table.currentValuationInr()).isNull();
    }

    @Test
    void savingAnEquityInvestmentAddsTheInvestorToTheCapTable() {
        Transaction saved = invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));

        CapTable table = capTable.current();

        assertThat(table.holdings()).extracting(CapTable.Holding::name).containsExactly("Founder", "Investor A");
        assertThat(holding(table, "Investor A").shares()).isEqualTo(250_000);
        assertThat(holding(table, "Investor A").pct()).isEqualByComparingTo("20.00");
        assertThat(holding(table, "Founder").shares()).isEqualTo(1_000_000);
        assertThat(holding(table, "Founder").pct()).isEqualByComparingTo("80.00");
        // The saved row records what was issued.
        assertThat(saved.sharesIssued()).isEqualTo(250_000);
        assertThat(saved.equityPct()).isEqualByComparingTo("20.00");
    }

    @Test
    void investorAEndsAt16PercentAfterInvestorBsRound() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));   // company worth ₹80 lakh, A puts in ₹20 lakh
        invest("Investor B", "20000000", "5000000", LocalDate.of(2026, 2, 10));  // company worth ₹2 crore, B puts in ₹50 lakh

        CapTable table = capTable.current();

        assertThat(table.totalShares()).isEqualTo(1_562_500);
        assertThat(holding(table, "Investor A").shares()).isEqualTo(250_000);
        assertThat(holding(table, "Investor A").pct()).isEqualByComparingTo("16.00");
        assertThat(holding(table, "Investor B").shares()).isEqualTo(312_500);
        assertThat(holding(table, "Investor B").pct()).isEqualByComparingTo("20.00");
        assertThat(holding(table, "Founder").pct()).isEqualByComparingTo("64.00");
    }

    @Test
    void theCurrentValuationIsTheLatestRoundsPostMoney() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        assertThat(capTable.current().currentValuationInr()).isEqualByComparingTo("10000000");

        invest("Investor B", "20000000", "5000000", LocalDate.of(2026, 2, 10));
        assertThat(capTable.current().currentValuationInr()).isEqualByComparingTo("25000000");
    }

    @Test
    void aRoundDatedBeforeTheLatestOneDoesNotMoveTheCurrentValuation() {
        invest("Investor B", "20000000", "5000000", LocalDate.of(2026, 2, 10));

        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));

        assertThat(capTable.current().currentValuationInr()).isEqualByComparingTo("25000000");
    }

    @Test
    void anInvestorWhoInvestsAgainHasOneRowWithBothHoldingsAdded() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        invest("investor a", "20000000", "5000000", LocalDate.of(2026, 2, 10));

        CapTable table = capTable.current();

        assertThat(table.holdings()).extracting(CapTable.Holding::name).containsExactly("Founder", "Investor A");
        assertThat(holding(table, "Investor A").shares()).isEqualTo(250_000 + 312_500);
    }

    @Test
    void aRejectedInvestmentLeavesTheCapTableAndValuationAlone() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        CapTable before = capTable.current();

        // ₹1 into a ₹100 crore company buys less than 0.01%.
        assertThatThrownBy(() -> invest("Investor B", "1000000000", "1", LocalDate.of(2026, 2, 10)))
                .isInstanceOf(TransactionRuleException.class);

        assertThat(capTable.current()).isEqualTo(before);
    }

    @Test
    void nonEquityFundingDoesNotTouchTheCapTable() {
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.LOAN, LocalDate.of(2026, 1, 10),
                new BigDecimal("500000"), null, "Some Bank", null));

        assertThat(capTable.current().holdings()).hasSize(1);
        assertThat(capTable.current().currentValuationInr()).isNull();
    }
}
