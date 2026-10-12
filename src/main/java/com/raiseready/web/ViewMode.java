package com.raiseready.web;

import jakarta.servlet.http.HttpSession;

/** Which side of the cap table the visitor is looking from. Kept in the HTTP session. */
public enum ViewMode {
    FOUNDER,
    INVESTOR;

    static final String SESSION_ATTRIBUTE = "viewMode";

    /** The mode stored in the session; Founder when there is no session yet or nothing has been chosen. */
    public static ViewMode from(HttpSession session) {
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof ViewMode mode) {
            return mode;
        }
        return FOUNDER;
    }
}
