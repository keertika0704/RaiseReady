package com.raiseready.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EquityChartTest {

    private static Investment round(String investor, String date, String equity) {
        return new Investment("Co", investor, LocalDate.parse(date), new BigDecimal("100000000"), new BigDecimal("1000000"), new BigDecimal(equity));
    }

    private final List<Investment> threeRounds = List.of(
            round("Sneha Iyer", "2025-02-18", "7.5"),
            round("Ananya Rao", "2022-04-12", "10"),
            round("Rohan Mehta", "2023-09-05", "12"));

    @Test
    void eachInvestorsEquityThenTheFoundersRemainderLast() {
        EquityChart.Result chart = EquityChart.of(threeRounds);

        assertThat(chart.segments()).extracting(BreakupSegment::label).containsExactly("Ananya Rao", "Rohan Mehta", "Sneha Iyer", "Founders");
        assertThat(chart.segments()).extracting(BreakupSegment::valueText).containsExactly("10%", "12%", "7.5%", "70.5%");
        assertThat(chart.soldPct()).isEqualByComparingTo("29.5");
        assertThat(chart.foundersPct()).isEqualByComparingTo("70.5");
        assertThat(chart.centreText()).isEqualTo("70.5%");
    }

    @Test
    void theFoundersSliceIsGreyAndTheInvestorsAreColoured() {
        EquityChart.Result chart = EquityChart.of(threeRounds);

        assertThat(chart.segments()).extracting(BreakupSegment::slot).containsExactly(1, 2, 3, 0);
        assertThat(chart.slices()).extracting(DonutSlice::slot).containsExactly(1, 2, 3, 0);
    }

    @Test
    void theArcsAreExactlyTheEquityShares() {
        EquityChart.Result chart = EquityChart.of(threeRounds);

        assertThat(chart.segments()).extracting(s -> s.sharePct().toPlainString()).containsExactly("10.0", "12.0", "7.5", "70.5");
        double drawn = chart.slices().stream().mapToDouble(s -> Double.parseDouble(s.dash().split(" ")[0])).sum();
        assertThat(drawn).isCloseTo(100 - 4 * 0.482, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void tooltipDetailIsAboutEquityOnlyNeverMoney() {
        EquityChart.Result chart = EquityChart.of(threeRounds);

        assertThat(chart.segments().get(0).detail()).isEqualTo("Since 12 Apr 2022 · 33.9% of the equity sold");
        assertThat(chart.segments().get(1).detail()).isEqualTo("Since 05 Sep 2023 · 40.7% of the equity sold");
        assertThat(chart.segments().get(3).detail()).isEqualTo("Not sold · 29.5% sold in 3 rounds");
        for (BreakupSegment s : chart.segments()) {
            assertThat(s.detail() + s.valueText()).doesNotContain("₹").doesNotContainIgnoringCase("valuation");
        }
    }

    @Test
    void anInvestorWhoBoughtTwiceIsOneSliceWithTheEquityAddedUp() {
        EquityChart.Result chart = EquityChart.of(List.of(
                round("A", "2022-01-01", "5"), round("B", "2023-01-01", "10"), round("A", "2024-01-01", "2.5")));

        assertThat(chart.segments()).extracting(BreakupSegment::label).containsExactly("A", "B", "Founders");
        assertThat(chart.segments().get(0).valueText()).isEqualTo("7.5%");
        assertThat(chart.segments().get(2).valueText()).isEqualTo("82.5%");
        assertThat(chart.segments().get(2).detail()).isEqualTo("Not sold · 17.5% sold in 3 rounds");
    }

    @Test
    void aSingleRoundSaysRoundNotRounds() {
        assertThat(EquityChart.of(List.of(round("A", "2022-01-01", "20"))).segments().get(1).detail()).isEqualTo("Not sold · 20% sold in 1 round");
    }

    @Test
    void allEquitySoldLeavesNoFoundersSlice() {
        EquityChart.Result chart = EquityChart.of(List.of(round("A", "2022-01-01", "60"), round("B", "2023-01-01", "40")));

        assertThat(chart.segments()).extracting(BreakupSegment::label).containsExactly("A", "B");
        assertThat(chart.foundersPct()).isEqualByComparingTo("0");
    }

    @Test
    void equityPastAHundredPercentNeverMakesTheFoundersNegative() {
        EquityChart.Result chart = EquityChart.of(List.of(round("A", "2022-01-01", "70"), round("B", "2023-01-01", "50")));

        assertThat(chart.foundersPct()).isEqualByComparingTo("0");
        assertThat(chart.segments()).extracting(BreakupSegment::label).containsExactly("A", "B");
    }

    @Test
    void noInvestmentsMeansNoChartAndTheFoundersOwnEverything() {
        EquityChart.Result chart = EquityChart.of(List.of());

        assertThat(chart.segments()).isEmpty();
        assertThat(chart.centreText()).isEqualTo("100%");
    }

    @Test
    void investorsPastTheColourLimitFoldIntoOtherButTheFoundersStayTheirOwnGreySlice() {
        List<Investment> many = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            many.add(round("Investor " + i, "2022-01-" + String.format("%02d", i), "2"));
        }

        EquityChart.Result chart = EquityChart.of(many);

        assertThat(chart.segments()).hasSize(MoneyBreakup.MAX_COLOURS + 2);          // 8 named + Other + Founders
        assertThat(chart.segments().get(8).label()).isEqualTo("Other (2 more)");
        assertThat(chart.segments().get(8).valueText()).isEqualTo("4%");
        assertThat(chart.segments().get(9).label()).isEqualTo("Founders");
        assertThat(chart.segments().get(9).valueText()).isEqualTo("80%");
    }
}
