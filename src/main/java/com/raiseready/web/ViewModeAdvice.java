package com.raiseready.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.raiseready.investor.InvestorPositionService;

/** Makes the current mode, page and (in Investor mode) the investor being viewed available to every page, for the shared navigation. */
@ControllerAdvice
public class ViewModeAdvice {

    private final InvestorPositionService investors;
    private final ViewingAs viewingAs;

    public ViewModeAdvice(InvestorPositionService investors, ViewingAs viewingAs) {
        this.investors = investors;
        this.viewingAs = viewingAs;
    }

    @ModelAttribute("viewMode")
    public ViewMode viewMode(HttpServletRequest request) {
        return ViewMode.from(request.getSession(false));
    }

    /** The path of the page being shown, without the context path, so the sidebar can mark the current link. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** The investors the "Viewing as" dropdown offers. Empty outside Investor mode, where there is no dropdown. */
    @ModelAttribute("investorNames")
    public List<String> investorNames(HttpServletRequest request) {
        return ViewMode.from(request.getSession(false)) == ViewMode.INVESTOR ? investors.investorNames() : List.of();
    }

    /** The investor Investor mode is showing, or null when there is none (or outside Investor mode). */
    @ModelAttribute("viewingAs")
    public String viewingAs(HttpServletRequest request) {
        return ViewMode.from(request.getSession(false)) == ViewMode.INVESTOR ? viewingAs.selected(request.getSession(false)).orElse(null) : null;
    }
}
