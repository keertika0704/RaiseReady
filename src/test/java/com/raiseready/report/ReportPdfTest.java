package com.raiseready.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.raiseready.captable.CapTable;
import com.raiseready.investor.InvestorPosition;
import com.raiseready.metrics.Metrics;
import com.raiseready.web.InrFormatter;
import com.raiseready.web.ViewMode;

/** The PDF drawn from hand-made reports: no database, so the edge cases (profitable, no valuation, no investor) can be set up exactly. */
class ReportPdfTest {

    private final ReportPdf pdf = new ReportPdf(new InrFormatter());

    private static CapTable capTable(BigDecimal valuation) {
        return new CapTable(List.of(new CapTable.Holding("Founder", 1_000_000, new BigDecimal("80.00")),
                new CapTable.Holding("Investor A", 250_000, new BigDecimal("20.00"))), 1_250_000, valuation);
    }

    private String text(Report report) throws IOException {
        byte[] bytes = pdf.render(report);
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        return PdfText.of(bytes);
    }

    @Test
    void founderReportHasTheFourMetricsTheValuationAndTheCapTable() throws IOException {
        Metrics metrics = new Metrics(new BigDecimal("4200000"), new BigDecimal("350000"), new BigDecimal("12.0"), new BigDecimal("90000"), 3);
        Report report = new Report("Acme", LocalDate.of(2026, 10, 13), ViewMode.FOUNDER, metrics, capTable(new BigDecimal("10000000")),
                new BigDecimal("10000000"), 2, null);

        String text = text(report);

        assertThat(text).contains(PdfText.s("Acme", "Founder report", "13 Oct 2026"));
        assertThat(text).contains(PdfText.s("Cash ₹42 lakh", "Monthly burn ₹3.5 lakh", "Runway 12.0 months", "MRR ₹90,000", "3 active subscriptions"));
        assertThat(text).contains(PdfText.s("Current valuation ₹1 Cr", "Funding rounds 2"));
        assertThat(text).contains(PdfText.s("Founder 10,00,000 80.00%", "Investor A 2,50,000 20.00%", "Total 12,50,000 100%"));
    }

    @Test
    void aProfitableCompanyShowsProfitableAndANegativeBurn() throws IOException {
        Metrics metrics = new Metrics(new BigDecimal("4200000"), new BigDecimal("-50000"), null, new BigDecimal("90000"), 3);

        String text = text(new Report("Acme", LocalDate.of(2026, 10, 13), ViewMode.FOUNDER, metrics, capTable(null), null, 0, null));

        assertThat(text).contains(PdfText.s("Runway Profitable", "Monthly burn -₹50,000", "Current valuation —"));
    }

    @Test
    void investorReportShowsTheirPositionAndNoCompanyFinancials() throws IOException {
        InvestorPosition position = new InvestorPosition("Investor A", new BigDecimal("2000000"), 250_000, new BigDecimal("20.00"), new BigDecimal("2000000"));

        String text = text(new Report("Acme", LocalDate.of(2026, 10, 13), ViewMode.INVESTOR, null, capTable(new BigDecimal("10000000")),
                new BigDecimal("10000000"), 1, position));

        assertThat(text).contains(PdfText.s("Investor report", "Viewing as Investor A", "Total invested ₹20 lakh", "Equity 20.00%", "Current value ₹20 lakh"));
        assertThat(text).contains(PdfText.s("Current valuation ₹1 Cr", "Investor A 2,50,000 20.00%"));
        assertThat(text).doesNotContain(PdfText.s("Cash", "Monthly burn", "Runway", "MRR", "Key metrics"));
    }

    @Test
    void investorReportBeforeAnyoneHasInvestedSaysSo() throws IOException {
        String text = text(new Report("Acme", LocalDate.of(2026, 10, 13), ViewMode.INVESTOR, null, capTable(null), null, 0, null));

        assertThat(text).contains(PdfText.s("No one has invested yet"));
    }

    @Test
    void theFileNameSaysWhichReportItIs() {
        Report report = new Report("Acme", LocalDate.of(2026, 10, 13), ViewMode.INVESTOR, null, capTable(null), null, 0, null);

        assertThat(report.fileName()).isEqualTo("raiseready-investor-report-2026-10-13.pdf");
    }
}
