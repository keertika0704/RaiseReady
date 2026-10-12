package com.raiseready.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ViewModeController {

    /** Stores the chosen mode in the session and sends the visitor back to the page they were on. */
    @PostMapping("/mode")
    public String switchMode(@RequestParam ViewMode mode, HttpServletRequest request) {
        request.getSession().setAttribute(ViewMode.SESSION_ATTRIBUTE, mode);
        return "redirect:" + returnPath(request.getHeader("Referer"));
    }

    /**
     * Only the path and query of the Referer are used, so the redirect always stays on this site.
     * Paths starting with "//" are refused because browsers read them as another host.
     */
    static String returnPath(String referer) {
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
