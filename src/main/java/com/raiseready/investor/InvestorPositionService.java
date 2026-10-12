package com.raiseready.investor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.raiseready.captable.CapTable;
import com.raiseready.captable.CapTableService;
import com.raiseready.transactions.TransactionRepository;

/** The investor's side of the numbers: how much they put in, what share of the company that is now, and what it is worth. */
@Service
public class InvestorPositionService {

    private final CapTableService capTable;
    private final TransactionRepository transactions;

    public InvestorPositionService(CapTableService capTable, TransactionRepository transactions) {
        this.capTable = capTable;
        this.transactions = transactions;
    }

    /** Everyone on the cap table except the founder, in the order they came in. */
    public List<String> investorNames() {
        return capTable.current().holdings().stream()
                .map(CapTable.Holding::name)
                .filter(name -> !CapTableService.FOUNDER.equalsIgnoreCase(name))
                .toList();
    }

    /**
     * @param name an investor on the cap table, in any capitalisation
     * @throws IllegalArgumentException if that is not an investor (nobody by that name, or the founder)
     */
    public InvestorPosition positionOf(String name) {
        CapTable table = capTable.current();
        CapTable.Holding holding = table.holdings().stream()
                .filter(h -> h.name().equalsIgnoreCase(name) && !CapTableService.FOUNDER.equalsIgnoreCase(h.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not an investor: " + name));

        // Worked from the shares themselves, not the rounded percentage, so the value is not off by the rounding.
        BigDecimal value = table.currentValuationInr() == null ? null
                : table.currentValuationInr().multiply(BigDecimal.valueOf(holding.shares()))
                        .divide(BigDecimal.valueOf(table.totalShares()), 2, RoundingMode.HALF_UP);
        return new InvestorPosition(holding.name(), transactions.totalEquityInvestedBy(holding.name()), holding.shares(), holding.pct(), value);
    }
}
