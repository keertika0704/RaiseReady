package com.raiseready.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ViewingAsController {

    private final ViewingAs viewingAs;

    public ViewingAsController(ViewingAs viewingAs) {
        this.viewingAs = viewingAs;
    }

    /** Stores which investor to view as and sends the visitor back to the page they were on. */
    @PostMapping("/viewing-as")
    public String choose(@RequestParam String investor, HttpServletRequest request) {
        if (!viewingAs.choose(request.getSession(), investor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is not an investor on the cap table.");
        }
        return "redirect:" + ViewModeController.returnPath(request.getHeader("Referer"));
    }
}
