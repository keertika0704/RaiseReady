package com.raiseready.report;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.captable.CapTable;
import com.raiseready.captable.CapTableService;
import com.raiseready.investor.InvestorPosition;
import com.raiseready.investor.InvestorPositionService;
import com.raiseready.metrics.MetricsService;
import com.raiseready.web.Investment;
import com.raiseready.web.InvestmentRepository;
import com.raiseready.web.ViewMode;

/**
 * Collects the report from the same services the Overview and Investments pages use, so there is no second way of working
 * out a number. Read-only, in one transaction, so the figures all come from the same moment.
 */
@Service
public class ReportService {

    private final MetricsService metrics;
    private final CapTableService capTable;
    private final InvestorPositionService investorPositions;
    private final InvestmentRepository investments;
    private final Clock clock;

    public ReportService(MetricsService metrics, CapTableService capTable, InvestorPositionService investorPositions,
                         InvestmentRepository investments, Clock clock) {
        this.metrics = metrics;
        this.capTable = capTable;
        this.investorPositions = investorPositions;
        this.investments = investments;
        this.clock = clock;
    }

    /**
     * @param mode     whose report it is. Investor mode leaves out the company's own financials, as the Overview does.
     * @param investor Investor mode: the investor being viewed (as chosen in "Viewing as"), or null if nobody has invested
     */
    @Transactional(readOnly = true)
    public Report build(ViewMode mode, String investor) {
        List<Investment> rounds = investments.findAllOldestFirst();
        CapTable table = capTable.current();
        boolean founder = mode == ViewMode.FOUNDER;
        InvestorPosition position = !founder && investor != null ? investorPositions.positionOf(investor) : null;
        return new Report(rounds.isEmpty() ? "RaiseReady" : rounds.get(0).companyName(), LocalDate.now(clock), mode,
                founder ? metrics.current() : null, table, table.currentValuationInr(), rounds.size(), position);
    }
}
