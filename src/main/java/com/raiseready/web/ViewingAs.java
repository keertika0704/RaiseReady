package com.raiseready.web;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

import com.raiseready.investor.InvestorPositionService;

/**
 * Which investor Investor mode is showing the numbers of. Kept in the HTTP session, like the mode itself.
 * Until one is chosen, or if the one chosen is no longer on the cap table, it is the first investor.
 */
@Component
public class ViewingAs {

    static final String SESSION_ATTRIBUTE = "viewingAs";

    private final InvestorPositionService investors;

    public ViewingAs(InvestorPositionService investors) {
        this.investors = investors;
    }

    /** The investor to show, or empty when nobody has invested yet. */
    public Optional<String> selected(HttpSession session) {
        List<String> names = investors.investorNames();
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof String chosen) {
            Optional<String> match = names.stream().filter(n -> n.equalsIgnoreCase(chosen)).findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return names.stream().findFirst();
    }

    /** Remembers the choice. Returns false, remembering nothing, if that is not an investor on the cap table. */
    public boolean choose(HttpSession session, String name) {
        Optional<String> match = investors.investorNames().stream().filter(n -> n.equalsIgnoreCase(name)).findFirst();
        match.ifPresent(n -> session.setAttribute(SESSION_ATTRIBUTE, n));
        return match.isPresent();
    }
}
