package com.raiseready.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.raiseready.transactions.TransactionRepository;

/**
 * Works the dashboard numbers out from the transactions every time they are asked for. Nothing is stored, so a
 * transaction added a second ago is already counted.
 * <ul>
 *   <li>Cash: all revenue and funding, minus all expenses.</li>
 *   <li>Burn: expenses minus revenue over the last {@value #WINDOW_DAYS} days.</li>
 *   <li>Runway: cash divided by burn, in months. "Profitable" (no figure) when burn is zero or less.</li>
 *   <li>MRR: Subscription revenue over the last {@value #WINDOW_DAYS} days.</li>
 *   <li>Active subscriptions: how many Subscription entries make up that MRR.</li>
 * </ul>
 */
@Service
public class MetricsService {

    /** The one "last 30 days" every windowed figure uses: today and the {@code WINDOW_DAYS - 1} days before it. */
    static final int WINDOW_DAYS = 30;

    private final TransactionRepository transactions;
    private final Clock clock;

    public MetricsService(TransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public Metrics current() {
        LocalDate today = LocalDate.now(clock);
        TransactionRepository.Totals totals = transactions.totals(today.minusDays(WINDOW_DAYS));

        BigDecimal cash = totals.cash();
        BigDecimal burn = totals.expenses().subtract(totals.revenue());
        return new Metrics(cash, burn, runway(cash, burn), totals.subscriptionRevenue(), totals.subscriptionCount());
    }

    /** Null means profitable. Cash that has already run out gives 0 months, never a negative number. */
    private static BigDecimal runway(BigDecimal cash, BigDecimal burn) {
        if (burn.signum() <= 0) {
            return null;
        }
        return cash.max(BigDecimal.ZERO).divide(burn, 1, RoundingMode.HALF_UP);
    }
}
