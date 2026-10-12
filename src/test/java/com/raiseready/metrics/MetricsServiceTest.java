package com.raiseready.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.raiseready.transactions.TransactionRepository;
import com.raiseready.transactions.TransactionRepository.Totals;

/**
 * The running example: ₹15 lakh in the bank, ₹2 lakh spent and ₹50,000 earned in the last 30 days. That burns
 * ₹1.5 lakh a month, so the cash lasts 10 months. Four of the revenue entries are subscriptions worth ₹40,000.
 */
class MetricsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T10:00:00Z"), ZoneOffset.UTC);

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final MetricsService service = new MetricsService(repository, CLOCK);

    private void totals(String cash, String expenses, String revenue, String subscriptionRevenue, int subscriptionCount) {
        when(repository.totals(TODAY.minusDays(30))).thenReturn(new Totals(
                new BigDecimal(cash), new BigDecimal(expenses), new BigDecimal(revenue), new BigDecimal(subscriptionRevenue), subscriptionCount));
    }

    private void example() {
        totals("1500000", "200000", "50000", "40000", 4);
    }

    @Test
    void cashIsWhatTheRepositoryTotalsAsRevenuePlusFundingMinusExpenses() {
        example();

        assertThat(service.current().cash()).isEqualByComparingTo("1500000");
    }

    @Test
    void burnIsExpensesMinusRevenueOverTheLast30Days() {
        example();

        assertThat(service.current().monthlyBurn()).isEqualByComparingTo("150000");
    }

    @Test
    void runwayIsCashDividedByBurn() {
        example();

        Metrics m = service.current();

        assertThat(m.runwayMonths()).isEqualByComparingTo("10");
        assertThat(m.profitable()).isFalse();
    }

    @Test
    void runwayIsProfitableWhenBurnIsZero() {
        totals("1500000", "50000", "50000", "0", 0);

        Metrics m = service.current();

        assertThat(m.monthlyBurn()).isEqualByComparingTo("0");
        assertThat(m.runwayMonths()).isNull();
        assertThat(m.profitable()).isTrue();
    }

    @Test
    void runwayIsProfitableWhenRevenueBeatsExpenses() {
        totals("1500000", "50000", "200000", "0", 0);

        Metrics m = service.current();

        assertThat(m.monthlyBurn()).isEqualByComparingTo("-150000");
        assertThat(m.profitable()).isTrue();
    }

    @Test
    void runwayIsZeroOnceCashHasRunOut() {
        totals("-40000", "200000", "50000", "0", 0);

        assertThat(service.current().runwayMonths()).isEqualByComparingTo("0");
    }

    @Test
    void mrrIsTheSubscriptionRevenueOfTheLast30Days() {
        example();

        assertThat(service.current().mrr()).isEqualByComparingTo("40000");
    }

    @Test
    void activeSubscriptionsIsTheNumberOfThoseSubscriptionEntries() {
        example();

        assertThat(service.current().activeSubscriptions()).isEqualTo(4);
    }

    @Test
    void windowIsThirtyDaysEndingToday() {
        example();

        service.current();

        // Literal 30 on purpose: changing the constant should be a deliberate edit to this test too.
        verify(repository).totals(LocalDate.of(2026, 5, 31));
        assertThat(MetricsService.WINDOW_DAYS).isEqualTo(30);
    }
}
