package com.raiseready.report;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.raiseready.web.ViewMode;
import com.raiseready.web.ViewingAs;

/**
 * The report in whichever mode the visitor is in, as a page and as a PDF. Both are built by {@link ReportService} from the
 * same numbers. Nothing is changed or saved.
 */
@Controller
public class ReportController {

    private final ReportService reports;
    private final ReportPdf pdf;
    private final ViewingAs viewingAs;

    public ReportController(ReportService reports, ReportPdf pdf, ViewingAs viewingAs) {
        this.reports = reports;
        this.pdf = pdf;
        this.viewingAs = viewingAs;
    }

    @GetMapping("/report")
    public String page(Model model, HttpSession session) {
        model.addAttribute("report", report(session));
        return "report";
    }

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> download(HttpSession session) {
        Report report = report(session);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.render(report));
    }

    private Report report(HttpSession session) {
        ViewMode mode = ViewMode.from(session);
        return reports.build(mode, mode == ViewMode.INVESTOR ? viewingAs.selected(session).orElse(null) : null);
    }
}
