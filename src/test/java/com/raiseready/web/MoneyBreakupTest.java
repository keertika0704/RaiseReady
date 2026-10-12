package com.raiseready.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.raiseready.transactions.Transaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionType;

class MoneyBreakupTest {

    private static MoneyBreakup.Entry entry(String label, String date, String amount) {
        return new MoneyBreakup.Entry(label, LocalDate.parse(date), new BigDecimal(amount));
    }

    private static Transaction tx(long id, TransactionType type, TransactionCategory category, String date, String amount, String investor) {
        return new Transaction(id, type, category, LocalDate.parse(date), new BigDecimal(amount), null, investor,
                category == TransactionCategory.EQUITY_INVESTMENT ? BigDecimal.TEN : null, null, null, category.isRecurring(), Instant.EPOCH);
    }

    @Test
    void oneSegmentPerLabelInOrderItFirstAppears() {
        List<BreakupSegment> segments = MoneyBreakup.group(List.of(
                entry("A", "2022-01-01", "2000000"),
                entry("B", "2023-01-01", "9600000"),
                entry("C", "2024-01-01", "15000000")));

        assertThat(segments).extracting(BreakupSegment::label).containsExactly("A", "B", "C");
        assertThat(segments).extracting(BreakupSegment::slot).containsExactly(1, 2, 3);
        assertThat(segments).extracting(s -> s.sharePct().toPlainString()).containsExactly("7.5", "36.1", "56.4");
    }

    @Test
    void aLabelThatAppearsTwiceIsOneSegmentWithOneColour() {
        List<BreakupSegment> segments = MoneyBreakup.group(List.of(
                entry("A", "2022-01-01", "1000000"),
                entry("B", "2023-01-01", "1000000"),
                entry("A", "2024-01-01", "2000000")));

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).label()).isEqualTo("A");
        assertThat(segments.get(0).amountInr()).isEqualByComparingTo("3000000");
        assertThat(segments.get(0).firstDate()).isEqualTo(LocalDate.parse("2022-01-01"));
        assertThat(segments.get(0).slot()).isEqualTo(1);
    }

    @Test
    void aFixedSlotKeepsItsColourWhateverElseIsPresent() {
        List<BreakupSegment> segments = MoneyBreakup.group(List.of(
                new MoneyBreakup.Entry("Travel", LocalDate.parse("2024-01-01"), new BigDecimal("100"), 5),
                new MoneyBreakup.Entry("Rent", LocalDate.parse("2024-01-02"), new BigDecimal("100"), 2)));

        assertThat(segments).extracting(BreakupSegment::slot).containsExactly(5, 2);
    }

    @Test
    void labelsPastTheColourLimitFoldIntoOther() {
        List<MoneyBreakup.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            entries.add(entry("Source " + i, "2022-01-" + String.format("%02d", i), "1000000"));
        }

        List<BreakupSegment> segments = MoneyBreakup.group(entries);

        assertThat(segments).hasSize(MoneyBreakup.MAX_COLOURS + 1);
        BreakupSegment other = segments.get(segments.size() - 1);
        assertThat(other.slot()).isZero();
        assertThat(other.label()).isEqualTo("Other (2 more)");
        assertThat(other.amountInr()).isEqualByComparingTo("2000000");
        assertThat(segments.stream().map(BreakupSegment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10000000");
    }

    @Test
    void greyEntriesGoLastAndDoNotUseUpAColour() {
        List<BreakupSegment> segments = MoneyBreakup.group(List.of(
                new MoneyBreakup.Entry("Rest", LocalDate.parse("2000-01-01"), new BigDecimal("70"), MoneyBreakup.GREY),
                entry("A", "2022-01-01", "10"),
                entry("B", "2023-01-01", "20")));

        assertThat(segments).extracting(BreakupSegment::label).containsExactly("A", "B", "Rest");
        assertThat(segments).extracting(BreakupSegment::slot).containsExactly(1, 2, 0);
    }

    @Test
    void valueTextComesFromTheFormatterGiven() {
        List<BreakupSegment> segments = MoneyBreakup.group(List.of(entry("A", "2022-01-01", "10")), v -> v + " units");

        assertThat(segments.get(0).valueText()).isEqualTo("10 units");
    }

    @Test
    void noMoneyMeansNoSegments() {
        assertThat(MoneyBreakup.group(List.of())).isEmpty();
    }

    @Test
    void donutArcsTileTheWholeRingInOrderAndOnlyLongArcsGetALabel() {
        List<DonutSlice> slices = MoneyBreakup.donut(MoneyBreakup.group(List.of(
                entry("A", "2022-01-01", "2000000"),
                entry("B", "2023-01-01", "9600000"),
                entry("C", "2024-01-01", "15000000"))));

        assertThat(slices).hasSize(3);
        // Each arc starts where the previous one ended (the 2px gap is taken off the arc's drawn length, not its start).
        double expectedStart = 0;
        double drawn = 0;
        for (DonutSlice slice : slices) {
            assertThat(Double.parseDouble(slice.offset())).isCloseTo(-expectedStart, org.assertj.core.data.Offset.offset(0.002));
            double dash = Double.parseDouble(slice.dash().split(" ")[0]);
            drawn += dash;
            expectedStart += dash + 0.482;
        }
        assertThat(drawn).isCloseTo(100 - 3 * 0.482, org.assertj.core.data.Offset.offset(0.1));
        assertThat(slices).extracting(DonutSlice::showLabel).containsExactly(false, true, true);
    }

    @Test
    void aSingleSliceFillsTheWholeRingWithoutAGap() {
        List<DonutSlice> slices = MoneyBreakup.donut(MoneyBreakup.group(List.of(entry("A", "2022-01-01", "5000000"))));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).dash()).startsWith("100.000");
        assertThat(slices.get(0).showLabel()).isTrue();
    }

    // ---- which transactions feed the two dashboard charts ----

    private final List<Transaction> ledger = List.of(
            tx(1, TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2022-04-12", "2000000", "Ananya Rao"),
            tx(2, TransactionType.FUNDING, TransactionCategory.LOAN, "2022-06-01", "500000", null),
            tx(3, TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, "2023-01-05", "100000", null),
            tx(4, TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, "2023-02-05", "100000", null),
            tx(5, TransactionType.EXPENSE, TransactionCategory.RENT, "2023-01-10", "30000", null),
            tx(6, TransactionType.EXPENSE, TransactionCategory.SALARIES, "2023-01-31", "70000", null),
            tx(7, TransactionType.EXPENSE, TransactionCategory.TRAVEL, "2023-02-03", "5000", null));

    @Test
    void moneyInIsFundingByInvestorOrKindPlusRevenueByCategoryAndNeverExpenses() {
        List<BreakupSegment> in = MoneyBreakup.group(PageController.moneyInEntries(ledger));

        assertThat(in).extracting(BreakupSegment::label).containsExactly("Ananya Rao", "Loan", "Subscription");
        assertThat(in.get(2).amountInr()).isEqualByComparingTo("200000");   // both subscription payments, one slice
        assertThat(in.stream().map(BreakupSegment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("2700000");
    }

    @Test
    void moneyOutIsExpensesByCategoryWithAColourThatDoesNotDependOnWhatElseIsPresent() {
        List<BreakupSegment> out = MoneyBreakup.group(PageController.moneyOutEntries(ledger));

        assertThat(out).extracting(BreakupSegment::label).containsExactly("Rent", "Salaries", "Travel");
        // Salaries is the first expense category, Rent the second, Travel the fifth, whether or not the others have spending.
        assertThat(out).extracting(BreakupSegment::slot).containsExactly(2, 1, 5);
        assertThat(out.stream().map(BreakupSegment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("105000");
    }

    @Test
    void withNoTransactionsBothChartsAreEmpty() {
        assertThat(MoneyBreakup.group(PageController.moneyInEntries(List.of()))).isEmpty();
        assertThat(MoneyBreakup.group(PageController.moneyOutEntries(List.of()))).isEmpty();
    }
}
