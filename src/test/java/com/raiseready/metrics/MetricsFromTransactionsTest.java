package com.raiseready.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionRepository;
import com.raiseready.transactions.TransactionType;

/** Real database, rolled back. Compares before and after so rows already in the table don't matter. */
@SpringBootTest
@Transactional
class MetricsFromTransactionsTest {

    @Autowired
    TransactionRepository repository;

    @Autowired
    MetricsService service;

    private void add(TransactionType type, TransactionCategory category, int daysAgo, String amount) {
        repository.insert(type, category, LocalDate.now().minusDays(daysAgo), new BigDecimal(amount), null, null, null, null, null, false);
    }

    @Test
    void newTransactionsChangeTheMetricsTheNextTimeTheyAreRead() {
        Metrics before = service.current();

        repository.insert(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, LocalDate.now().minusDays(200), new BigDecimal("1000000.00"), null, "Ananya Rao", new BigDecimal("10"), new BigDecimal("9000000.00"), null, false);
        add(TransactionType.EXPENSE, TransactionCategory.SALARIES, 5, "30000.00");          // inside the window
        add(TransactionType.EXPENSE, TransactionCategory.TRAVEL, 120, "7000.00");           // old: cash only
        add(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, 3, "12000.00");      // inside: MRR + count
        add(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, 29, "8000.00");      // last day inside the window
        add(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, 30, "5000.00");      // 30 days ago is out
        add(TransactionType.REVENUE, TransactionCategory.ONE_TIME_SALE, 2, "9000.00");      // revenue, but not a subscription

        Metrics after = service.current();

        // cash: +1,000,000 funding +12,000 +8,000 +5,000 +9,000 revenue -30,000 -7,000 expenses
        assertThat(after.cash().subtract(before.cash())).isEqualByComparingTo("997000");
        // burn: 30,000 expenses - (12,000 + 8,000 + 9,000) revenue in the window
        assertThat(after.monthlyBurn().subtract(before.monthlyBurn())).isEqualByComparingTo("1000");
        assertThat(after.mrr().subtract(before.mrr())).isEqualByComparingTo("20000");
        assertThat(after.activeSubscriptions() - before.activeSubscriptions()).isEqualTo(2);
    }
}
