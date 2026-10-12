package com.raiseready;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ViewModeTest {

    @Autowired
    MockMvc mvc;

    @Test
    void defaultsToFounderOnEveryPage() throws Exception {
        for (String page : new String[] {"/", "/investments"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"mode-toggle\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("mode-founder")));
        }
    }

    @Test
    void chosenModeIsKeptInTheSessionAcrossPages() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session).header("Referer", "http://localhost/investments"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/investments"));

        for (String page : new String[] {"/", "/investments"}) {
            mvc.perform(get(page).session(session))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("mode-investor")));
        }

        mvc.perform(post("/mode").param("mode", "FOUNDER").session(session))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mode-founder")));
    }

    @Test
    void anotherSessionIsNotAffected() throws Exception {
        mvc.perform(post("/mode").param("mode", "INVESTOR").session(new MockHttpSession()));
        mvc.perform(get("/").session(new MockHttpSession()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mode-founder")));
    }

    @Test
    void rejectsUnknownMode() throws Exception {
        mvc.perform(post("/mode").param("mode", "ADMIN")).andExpect(status().isBadRequest());
    }

    @Test
    void theTwoDonutChartsAreOnlyOnTheFounderDashboard() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h2>Money in</h2>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h2>Money out</h2>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"donut\"")));

        mvc.perform(post("/mode").param("mode", "INVESTOR").session(session));
        mvc.perform(get("/").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Money in"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Money out"))));
    }

    @Test
    void equityChartIsOnTheInvestmentsPageInBothModesAndIsAboutEquityOnly() throws Exception {
        MockHttpSession session = new MockHttpSession();

        for (String mode : new String[] {"FOUNDER", "INVESTOR"}) {
            mvc.perform(post("/mode").param("mode", mode).session(session));
            mvc.perform(get("/investments").session(session))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Who owns the company")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-name=\"Ananya Rao\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-amount=\"10%\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-extra=\"Since 12 Apr 2022 · 33.9% of the equity sold\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-name=\"Founders\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-extra=\"Not sold · 29.5% sold in 3 rounds\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("swatch s0")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/charts.js")))
                    // no money in the chart's own marks
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("valuation\""))));
        }
    }
}
