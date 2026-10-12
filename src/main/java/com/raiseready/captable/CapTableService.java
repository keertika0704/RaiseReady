package com.raiseready.captable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the cap table in step with equity investments: sizes each one with {@link DilutionService}, gives the
 * investor their shares and keeps the company's current valuation up to date. Call {@link #issue} and
 * {@link #refreshCurrentValuation} inside the same database transaction that saves the investment, so the
 * shares and the transaction row are saved together or not at all.
 */
@Service
public class CapTableService {

    /** The name of the holder every share starts with. Not an investor. */
    public static final String FOUNDER = "Founder";

    /** More new shares than this in one round means the pre-money is not a believable figure for the amount. */
    private static final long MAX_SHARES_PER_ROUND = 1_000_000_000_000L;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ShareholderRepository shareholders;
    private final CompanyRepository company;
    private final DilutionService dilution;

    public CapTableService(ShareholderRepository shareholders, CompanyRepository company, DilutionService dilution) {
        this.shareholders = shareholders;
        this.company = company;
        this.dilution = dilution;
    }

    /**
     * Creates the investor's shares for an equity round, adding them to what they already hold if they have invested before.
     *
     * @throws CapTableRuleException if the round cannot be put on the cap table; nothing is added
     */
    @Transactional
    public Dilution issue(String investor, BigDecimal preMoneyInr, BigDecimal investmentInr) {
        if (FOUNDER.equalsIgnoreCase(investor)) {
            throw new CapTableRuleException("investorName", "\"" + FOUNDER + "\" is the founder on the cap table. Use the investor's own name.");
        }

        Dilution result = size(preMoneyInr, investmentInr, shareholders.totalSharesLocked());

        shareholders.addShares(investor, result.newShares());
        return result;
    }

    /**
     * Sizes a round against {@code existingShares} with {@link DilutionService} and applies the rules a round must meet to go
     * on the cap table. Changes nothing, so the simulator can ask the same question {@link #issue} does.
     *
     * @throws CapTableRuleException if the round cannot be put on the cap table
     */
    Dilution size(BigDecimal preMoneyInr, BigDecimal investmentInr, long existingShares) {
        Dilution result;
        try {
            result = dilution.calculate(preMoneyInr, investmentInr, existingShares);
        } catch (ArithmeticException tooMany) {
            throw new CapTableRuleException("preMoneyValuation", "That pre-money valuation is too small for this amount.");
        }
        if (result.newShares() > MAX_SHARES_PER_ROUND) {
            throw new CapTableRuleException("preMoneyValuation", "That pre-money valuation is too small for this amount.");
        }
        if (result.newShares() < 1 || result.equityPct().signum() == 0) {
            throw new CapTableRuleException("amount", "That is too little to buy a share (under 0.01%) at this valuation.");
        }
        return result;
    }

    /** Sets the company's current valuation to the latest round's post-money. Call it after that round's transaction is saved. */
    @Transactional
    public void refreshCurrentValuation() {
        company.refreshCurrentValuation();
    }

    public CapTable current() {
        List<Shareholder> holders = shareholders.findAll();
        long total = holders.stream().mapToLong(Shareholder::shares).sum();
        List<CapTable.Holding> holdings = holders.stream()
                .map(h -> new CapTable.Holding(h.name(), h.shares(), percent(h.shares(), total)))
                .toList();
        return new CapTable(holdings, total, company.currentValuation());
    }

    static BigDecimal percent(long shares, long total) {
        return BigDecimal.valueOf(shares).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
