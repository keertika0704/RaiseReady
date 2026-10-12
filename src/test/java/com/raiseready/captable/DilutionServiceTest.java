package com.raiseready.captable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * The running example. The founder holds all 10,00,000 shares.
 * Investor A puts in ₹20 lakh when the company is worth ₹80 lakh, and so owns 20%.
 * Investor B then puts in ₹50 lakh when the company is worth ₹2 crore, and owns 20%, which dilutes A to 16%.
 * A's ₹20 lakh is by then worth ₹40 lakh: 16% of the ₹2.5 crore the company is worth after B's round.
 */
class DilutionServiceTest {

    private final DilutionService service = new DilutionService();

    private static BigDecimal rupees(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    void postMoneyIsPreMoneyPlusTheInvestment() {
        Dilution a = service.calculate(rupees("8000000"), rupees("2000000"), 1_000_000);

        assertThat(a.postMoneyValuationInr()).isEqualByComparingTo("10000000");
    }

    @Test
    void equityIsTheInvestmentOverPostMoney() {
        Dilution a = service.calculate(rupees("8000000"), rupees("2000000"), 1_000_000);

        assertThat(a.equityPct()).isEqualByComparingTo("20.00");
    }

    @Test
    void newSharesMakeTheInvestorAsBigAHolderAsTheEquity() {
        Dilution a = service.calculate(rupees("8000000"), rupees("2000000"), 1_000_000);

        // 250,000 of the 1,250,000 shares that exist afterwards is 20%.
        assertThat(a.newShares()).isEqualTo(250_000);
    }

    @Test
    void investorAEndsAt16PercentAfterInvestorBsRound() {
        long founder = 1_000_000;
        Dilution a = service.calculate(rupees("8000000"), rupees("2000000"), founder);
        long totalAfterA = founder + a.newShares();

        Dilution b = service.calculate(rupees("20000000"), rupees("5000000"), totalAfterA);
        long totalAfterB = totalAfterA + b.newShares();

        assertThat(b.postMoneyValuationInr()).isEqualByComparingTo("25000000");
        assertThat(b.equityPct()).isEqualByComparingTo("20.00");
        assertThat(b.newShares()).isEqualTo(312_500);
        assertThat(totalAfterB).isEqualTo(1_562_500);
        // A did not sell or buy anything: their 250,000 shares are just a smaller slice of a bigger pie.
        assertThat(BigDecimal.valueOf(a.newShares()).multiply(rupees("100")).divide(BigDecimal.valueOf(totalAfterB), 2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("16.00");
    }

    @Test
    void newSharesAreRoundedToAWholeShare() {
        // 1,000,000 * 1,000 / 30,000 = 33,333.33
        Dilution d = service.calculate(rupees("30000"), rupees("1000"), 1_000_000);

        assertThat(d.newShares()).isEqualTo(33_333);
        assertThat(d.equityPct()).isEqualByComparingTo("3.23");
    }

    @Test
    void amountsAndSharesMustBePositive() {
        assertThatThrownBy(() -> service.calculate(rupees("0"), rupees("1000"), 1_000_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(rupees("1000"), rupees("0"), 1_000_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(rupees("1000"), rupees("1000"), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAbsurdlySmallPreMoneyOverflowsInsteadOfWrappingAround() {
        assertThatThrownBy(() -> service.calculate(rupees("0.01"), rupees("9999999999999.99"), 1_000_000)).isInstanceOf(ArithmeticException.class);
    }
}
