package com.raiseready.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.raiseready.captable.CapTableRuleException;
import com.raiseready.captable.SimulatorService;

import jakarta.validation.Valid;

/**
 * The founder's what-if page. Both routes are GET: asking a question never changes anything, so there is no POST here to
 * save by accident. Investor mode never gets here: {@link FounderOnlyInterceptor} answers 403 first.
 */
@Controller
public class SimulatorController {

    private final SimulatorService simulator;

    public SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/simulator")
    public String blank(Model model) {
        model.addAttribute("form", new SimulatorForm());
        return "simulator";
    }

    /** The form always sends both fields (empty if left blank), so a request with both is a question to answer. */
    @GetMapping(value = "/simulator", params = {"amount", "preMoneyValuation"})
    public String simulate(@Valid @ModelAttribute("form") SimulatorForm form, BindingResult result, Model model) {
        if (!result.hasErrors()) {
            try {
                model.addAttribute("simulation", simulator.simulate(form.getPreMoneyValuation(), form.getAmount()));
            } catch (CapTableRuleException e) {
                result.rejectValue(e.getField(), "rule", e.getMessage());
            }
        }
        return "simulator";
    }
}
