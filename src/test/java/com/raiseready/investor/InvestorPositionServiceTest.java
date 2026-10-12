package com.raiseready.investor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.captable.CapTableFixture;
import com.raiseready.transactions.NewTransaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/**
 * The running example: the founder holds 10,00,000 shares. Investor A puts in ₹20 lakh at a ₹80 lakh pre-money (20%).
 * Investor B puts in ₹50 lakh at a ₹2 crore pre-money (20%), so the company is worth ₹2.5 crore and A is diluted to 16%.
 * Real database, rolled back.
 */
@SpringBootTest
@Transactional
class InvestorPositionServiceTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @Autowired
    InvestorPositionService service;

    @BeforeEach
    void startFromTheFounderHoldingEverything() {
        CapTableFixture.reset(jdbc);
    }

    private void invest(String investor, String preMoney, String amount, LocalDate on) {
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, on,
                new BigDecimal(amount), null, investor, new BigDecimal(preMoney)));
    }

    private void exampleRounds() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        invest("Investor B", "20000000", "5000000", LocalDate.of(2026, 2, 10));
    }

    @Test
    void investorAIsWorthFortyLakhInTheExample() {
        exampleRounds();

        InvestorPosition a = service.positionOf("Investor A");

        // 16% of the ₹2.5 crore the company is worth after B's round.
        assertThat(a.equityPct()).isEqualByComparingTo("16.00");
        assertThat(a.currentValueInr()).isEqualByComparingTo("4000000");
    }

    @Test
    void totalInvestedIsAllTheMoneyTheInvestorPutIn() {
        exampleRounds();

        assertThat(service.positionOf("Investor A").totalInvestedInr()).isEqualByComparingTo("2000000");
        assertThat(service.positionOf("Investor B").totalInvestedInr()).isEqualByComparingTo("5000000");
    }

    @Test
    void equityIsTheShareOfAllSharesTheInvestorHoldsNow() {
        exampleRounds();

        InvestorPosition b = service.positionOf("Investor B");

        assertThat(b.shares()).isEqualTo(312_500);
        assertThat(b.equityPct()).isEqualByComparingTo("20.00");
    }

    @Test
    void currentValueIsEquityTimesTheCurrentValuation() {
        exampleRounds();

        assertThat(service.positionOf("Investor B").currentValueInr()).isEqualByComparingTo("5000000");
    }

    @Test
    void anInvestorWhoInvestedBeforeTheLatestRoundSeesTheirValueGrow() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        // Straight after their own round, a 20% stake in a ₹1 crore company.
        assertThat(service.positionOf("Investor A").currentValueInr()).isEqualByComparingTo("2000000");

        invest("Investor B", "20000000", "5000000", LocalDate.of(2026, 2, 10));
        assertThat(service.positionOf("Investor A").currentValueInr()).isEqualByComparingTo("4000000");
    }

    @Test
    void anInvestorWhoInvestedTwiceHasOneTotalAndOneHolding() {
        invest("Investor A", "8000000", "2000000", LocalDate.of(2026, 1, 10));
        invest("investor a", "20000000", "5000000", LocalDate.of(2026, 2, 10));

        InvestorPosition a = service.positionOf("INVESTOR A");

        assertThat(a.totalInvestedInr()).isEqualByComparingTo("7000000");
        assertThat(a.shares()).isEqualTo(250_000 + 312_500);
        assertThat(service.investorNames()).containsExactly("Investor A");
    }

    @Test
    void valueIsWorkedFromTheSharesNotFromTheRoundedPercentage() {
        // ₹1,000 at a ₹29,000 pre-money buys 3.3333…% of a ₹30,000 company: worth ₹1,000, where 3.33% would say ₹999.
        invest("Investor A", "29000", "1000", LocalDate.of(2026, 1, 10));

        assertThat(service.positionOf("Investor A").currentValueInr()).isCloseTo(new BigDecimal("1000"), within(new BigDecimal("0.05")));
    }

    @Test
    void investorNamesListsEveryInvestorInOrderAndNeverTheFounder() {
        assertThat(service.investorNames()).isEmpty();

        exampleRounds();

        assertThat(service.investorNames()).containsExactly("Investor A", "Investor B");
    }

    @Test
    void theFounderAndUnknownNamesAreNotInvestors() {
        exampleRounds();

        assertThatThrownBy(() -> service.positionOf("Founder")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.positionOf("Nobody")).isInstanceOf(IllegalArgumentException.class);
    }
}
