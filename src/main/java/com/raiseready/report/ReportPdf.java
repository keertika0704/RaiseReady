package com.raiseready.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import com.raiseready.captable.CapTable;
import com.raiseready.web.InrFormatter;

/**
 * Draws a {@link Report} as a PDF. Every figure is written with the same {@link InrFormatter} and wording the pages use
 * (₹2.5 Cr, 16.00%, 3 months), so the PDF reads exactly like the dashboard. The font is bundled because the standard PDF
 * fonts have no ₹ sign.
 */
@Component
public class ReportPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color MUTED = new Color(0x5B, 0x64, 0x70);
    private static final Color LINE = new Color(0xD7, 0xDB, 0xE0);

    private final InrFormatter inr;
    private final BaseFont regular;
    private final BaseFont bold;

    public ReportPdf(InrFormatter inr) {
        this.inr = inr;
        this.regular = load("DejaVuSans.ttf");
        this.bold = load("DejaVuSans-Bold.ttf");
    }

    private static BaseFont load(String file) {
        try (InputStream in = ReportPdf.class.getResourceAsStream("/fonts/" + file)) {
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Cannot load the report font " + file, e);
        }
    }

    public byte[] render(Report report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(pdf, out);
            pdf.addTitle(report.companyName() + " report");
            pdf.addCreator("RaiseReady");
            pdf.open();

            pdf.add(new Paragraph(report.companyName(), font(bold, 20, Color.BLACK)));
            pdf.add(new Paragraph((report.founderView() ? "Founder report" : "Investor report") + " · " + report.generatedOn().format(DATE),
                    font(regular, 10, MUTED)));

            if (report.founderView()) {
                metrics(pdf, report);
            }
            valuation(pdf, report);
            if (!report.founderView()) {
                position(pdf, report);
            }
            capTable(pdf, report.capTable());
            pdf.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Cannot build the report PDF", e);
        }
        return out.toByteArray();
    }

    private void metrics(Document pdf, Report report) throws DocumentException {
        var m = report.metrics();
        heading(pdf, "Key metrics", "Burn and MRR cover the last 30 days.");
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.addCell(figure("Cash", inr.format(m.cash()), null));
        grid.addCell(figure("Monthly burn", inr.format(m.monthlyBurn()), null));
        grid.addCell(figure("Runway", m.profitable() ? "Profitable" : m.runwayMonths() + " months", null));
        grid.addCell(figure("MRR", inr.format(m.mrr()), m.activeSubscriptions() + " active subscriptions"));
        pdf.add(grid);
    }

    private void valuation(Document pdf, Report report) throws DocumentException {
        heading(pdf, "Valuation", null);
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.addCell(figure("Current valuation", report.currentValuationInr() != null ? inr.format(report.currentValuationInr()) : "—",
                report.currentValuationInr() != null ? "after the latest round" : null));
        grid.addCell(figure("Funding rounds", String.valueOf(report.rounds()), null));
        pdf.add(grid);
    }

    private void position(Document pdf, Report report) throws DocumentException {
        var p = report.position();
        if (p == null) {
            heading(pdf, "Your position", "No one has invested yet, so there are no investor numbers to show.");
            return;
        }
        heading(pdf, "Viewing as " + p.name(), null);
        PdfPTable grid = new PdfPTable(3);
        grid.setWidthPercentage(100);
        grid.addCell(figure("Total invested", inr.format(p.totalInvestedInr()), null));
        grid.addCell(figure("Equity", p.equityPct() + "%", null));
        grid.addCell(figure("Current value", p.currentValueInr() != null ? inr.format(p.currentValueInr()) : "—", null));
        pdf.add(grid);
    }

    private void capTable(Document pdf, CapTable table) throws DocumentException {
        heading(pdf, "Cap table", "Shares held by each person, and their share of the company right now.");
        PdfPTable grid = new PdfPTable(new float[] {3, 2, 2});
        grid.setWidthPercentage(100);
        grid.setHeaderRows(1);
        grid.addCell(cell("Holder", bold, Element.ALIGN_LEFT, true));
        grid.addCell(cell("Shares", bold, Element.ALIGN_RIGHT, true));
        grid.addCell(cell("Ownership", bold, Element.ALIGN_RIGHT, true));
        for (CapTable.Holding h : table.holdings()) {
            grid.addCell(cell(h.name(), regular, Element.ALIGN_LEFT, false));
            grid.addCell(cell(inr.shares(h.shares()), regular, Element.ALIGN_RIGHT, false));
            grid.addCell(cell(h.pct() + "%", regular, Element.ALIGN_RIGHT, false));
        }
        grid.addCell(cell("Total", bold, Element.ALIGN_LEFT, false));
        grid.addCell(cell(inr.shares(table.totalShares()), bold, Element.ALIGN_RIGHT, false));
        grid.addCell(cell("100%", bold, Element.ALIGN_RIGHT, false));
        pdf.add(grid);
    }

    private void heading(Document pdf, String title, String note) throws DocumentException {
        Paragraph p = new Paragraph(title, font(bold, 13, Color.BLACK));
        p.setSpacingBefore(22);
        pdf.add(p);
        if (note != null) {
            pdf.add(new Paragraph(note, font(regular, 9, MUTED)));
        }
        pdf.add(new Paragraph(" ", font(regular, 4, MUTED)));
    }

    /** A label over a big figure, with an optional small line beneath. */
    private PdfPCell figure(String label, String value, String detail) {
        Phrase text = new Phrase();
        text.add(new org.openpdf.text.Chunk(label + "\n", font(regular, 9, MUTED)));
        text.add(new org.openpdf.text.Chunk(value, font(bold, 16, Color.BLACK)));
        if (detail != null) {
            text.add(new org.openpdf.text.Chunk("\n" + detail, font(regular, 9, MUTED)));
        }
        PdfPCell cell = new PdfPCell(text);
        cell.setBorderColor(LINE);
        cell.setPadding(10);
        return cell;
    }

    private PdfPCell cell(String text, BaseFont face, int align, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(face, 10, Color.BLACK)));
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(header ? Color.BLACK : LINE);
        cell.setPadding(6);
        return cell;
    }

    private static Font font(BaseFont face, float size, Color color) {
        return new Font(face, size, Font.NORMAL, color);
    }
}
