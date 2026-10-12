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
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

/**
 * Real database, rolled back. Starts from the running example after both rounds: founder 10,00,000 shares (64%),
 * Investor A 2,50,000 (16%), Investor B 3,12,500 (20%), 15,62,500 in all.
 */
@SpringBootTest
@Transactional
class SimulatorServiceTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionService transactions;

    @Autowired
    CapTableService capTable;

    @Autowired
    DilutionService dilution;

    @Autowired
    SimulatorService simulator;

    @BeforeEach
    void investorAThenInvestorB() {
        CapTableFixture.reset(jdbc);
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 1, 10),
                new BigDecimal("2000000"), null, "Investor A", new BigDecimal("8000000")));
        transactions.add(new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.of(2026, 2, 10),
                new BigDecimal("5000000"), null, "Investor B", new BigDecimal("20000000")));
    }

    private static Simulation.Row row(Simulation s, String name) {
        return s.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theNumbersAreExactlyWhatDilutionServiceSaysForTheRealShareCount() {
        Simulation s = simulator.simulate(new BigDecimal("25000000"), new BigDecimal("5000000"));

        assertThat(s.dilution()).isEqualTo(dilution.calculate(new BigDecimal("25000000"), new BigDecimal("5000000"), 1_562_500));
        assertThat(s.totalBefore()).isEqualTo(1_562_500);
        assertThat(s.totalAfter()).isEqualTo(1_562_500 + s.dilution().newShares());
    }

    @Test
    void everyoneIsDilutedAndThePretendInvestorGetsTheirEquity() {
        // ₹50 lakh at ₹2.5 crore pre-money buys 16.67%, with 3,12,500 new shares on 15,62,500.
        Simulation s = simulator.simulate(new BigDecimal("25000000"), new BigDecimal("5000000"));

        assertThat(s.dilution().postMoneyValuationInr()).isEqualByComparingTo("30000000");
        assertThat(s.dilution().newShares()).isEqualTo(312_500);
        assertThat(s.totalAfter()).isEqualTo(1_875_000);

        assertThat(row(s, "Founder").pctBefore()).isEqualByComparingTo("64.00");
        assertThat(row(s, "Founder").pctAfter()).isEqualByComparingTo("53.33");
        assertThat(row(s, "Investor A").pctBefore()).isEqualByComparingTo("16.00");
        assertThat(row(s, "Investor A").pctAfter()).isEqualByComparingTo("13.33");
        assertThat(row(s, "Investor B").pctBefore()).isEqualByComparingTo("20.00");
        assertThat(row(s, "Investor B").pctAfter()).isEqualByComparingTo("16.67");

        Simulation.Row pretend = row(s, SimulatorService.PRETEND_INVESTOR);
        assertThat(pretend.pretend()).isTrue();
        assertThat(pretend.sharesBefore()).isZero();
        assertThat(pretend.sharesAfter()).isEqualTo(312_500);
        assertThat(pretend.pctAfter()).isEqualByComparingTo(s.dilution().equityPct());
        // Real holders keep every share they have; only their slice of the total shrinks.
        assertThat(s.rows().stream().filter(r -> !r.pretend())).allSatisfy(r -> assertThat(r.sharesAfter()).isEqualTo(r.sharesBefore()));
        assertThat(s.rows().get(s.rows().size() - 1).pretend()).isTrue();
    }

    @Test
    void theRealCapTableIsTheSameAfterASimulation() {
        CapTable tableBefore = capTable.current();
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);

        simulator.simulate(new BigDecimal("25000000"), new BigDecimal("5000000"));
        simulator.simulate(new BigDecimal("1000000"), new BigDecimal("9000000"));

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
        assertThat(capTable.current()).isEqualTo(tableBefore);
        assertThat(capTable.current().totalShares()).isEqualTo(1_562_500);
        assertThat(capTable.current().currentValuationInr()).isEqualByComparingTo("25000000");
    }

    @Test
    void aRoundThatCouldNotBeIssuedCannotBeSimulatedEitherAndChangesNothing() {
        CapTableSnapshot before = CapTableSnapshot.take(jdbc);

        // Too little to buy a single share at this valuation.
        assertThatThrownBy(() -> simulator.simulate(new BigDecimal("999999999999"), new BigDecimal("1")))
                .isInstanceOf(CapTableRuleException.class).extracting("field").isEqualTo("amount");
        // A pre-money absurdly small for the amount.
        assertThatThrownBy(() -> simulator.simulate(new BigDecimal("0.01"), new BigDecimal("9999999999999.99")))
                .isInstanceOf(CapTableRuleException.class).extracting("field").isEqualTo("preMoneyValuation");

        assertThat(CapTableSnapshot.take(jdbc)).isEqualTo(before);
    }

    @Test
    void theFirstRoundOnAFounderOnlyCapTableCanBeSimulated() {
        CapTableFixture.reset(jdbc);

        Simulation s = simulator.simulate(new BigDecimal("8000000"), new BigDecimal("2000000"));

        assertThat(s.rows()).hasSize(2);
        assertThat(row(s, "Founder").pctAfter()).isEqualByComparingTo("80.00");
        assertThat(row(s, SimulatorService.PRETEND_INVESTOR).pctAfter()).isEqualByComparingTo("20.00");
        assertThat(capTable.current().holdings()).hasSize(1);
    }
}
