package com.raiseready.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewModeControllerTest {

    @Test
    void redirectNeverLeavesThisSite() {
        assertEquals("/", ViewModeController.returnPath(null));
        assertEquals("/", ViewModeController.returnPath("http://evil.example//attacker.com"));
        assertEquals("/", ViewModeController.returnPath("not a url"));
        assertEquals("/investments?x=1", ViewModeController.returnPath("http://localhost:8080/investments?x=1"));
    }
}
