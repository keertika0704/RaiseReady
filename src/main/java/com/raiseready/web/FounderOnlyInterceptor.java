package com.raiseready.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/** Stops Investor mode before a founder-only page's controller runs: the response is 403 and nothing is read or saved. */
public class FounderOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (ViewMode.from(request.getSession(false)) != ViewMode.FOUNDER) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Switch to Founder to use this page.");
            return false;
        }
        return true;
    }
}
