package com.raiseready.captable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What if" for the founder: shows how a pretend round would change who owns what. The sum is the real one
 * ({@link DilutionService}, with the same rules as {@link CapTableService#issue}), but nothing is saved. The
 * transaction is read-only, so the database itself refuses a write even if one were added here by mistake.
 */
@Service
public class SimulatorService {

    /** The name the pretend investor goes by in the table. */
    public static final String PRETEND_INVESTOR = "New investor";

    private final CapTableService capTable;

    public SimulatorService(CapTableService capTable) {
        this.capTable = capTable;
    }

    /**
     * @throws CapTableRuleException if a real round of this size could not be put on the cap table
     */
    @Transactional(readOnly = true)
    public Simulation simulate(BigDecimal preMoneyInr, BigDecimal investmentInr) {
        CapTable now = capTable.current();
        Dilution dilution = capTable.size(preMoneyInr, investmentInr, now.totalShares());
        long totalAfter = now.totalShares() + dilution.newShares();

        List<Simulation.Row> rows = new ArrayList<>();
        for (CapTable.Holding h : now.holdings()) {
            rows.add(new Simulation.Row(h.name(), h.shares(), h.pct(), h.shares(), CapTableService.percent(h.shares(), totalAfter), false));
        }
        rows.add(new Simulation.Row(PRETEND_INVESTOR, 0, BigDecimal.ZERO.setScale(2), dilution.newShares(),
                CapTableService.percent(dilution.newShares(), totalAfter), true));
        return new Simulation(preMoneyInr, investmentInr, dilution, rows, now.totalShares(), totalAfter);
    }
}
