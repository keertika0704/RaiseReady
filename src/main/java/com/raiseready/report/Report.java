package com.raiseready.report;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.raiseready.captable.CapTable;
import com.raiseready.investor.InvestorPosition;
import com.raiseready.metrics.Metrics;
import com.raiseready.web.ViewMode;

/**
 * Everything the report shows, gathered once so the report page and the PDF are drawn from the very same numbers.
 *
 * @param metrics             Founder mode only; null in Investor mode, which never shows the company's cash, burn, runway or MRR
 * @param currentValuationInr the company's latest post-money valuation; null if there has been no equity round
 * @param rounds              how many funding rounds the Overview counts
 * @param position            Investor mode only: the investor being viewed; null if nobody has invested yet
 */
public record Report(String companyName, LocalDate generatedOn, ViewMode mode, Metrics metrics, CapTable capTable,
                     BigDecimal currentValuationInr, int rounds, InvestorPosition position) {

    public boolean founderView() {
        return mode == ViewMode.FOUNDER;
    }

    /** The file name the PDF downloads as. */
    public String fileName() {
        return "raiseready-" + mode.name().toLowerCase() + "-report-" + generatedOn + ".pdf";
    }
}
