package com.raiseready.transactions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * Kept thin on purpose: bind and check the form, hand it to {@link TransactionService}, choose the page.
 * Investor mode never gets here: {@link com.raiseready.web.FounderOnlyInterceptor} answers 403 first.
 */
@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactions;
    private final Clock clock;

    public TransactionController(TransactionService transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("transactions", transactions.listNewestFirst());
        return "transactions";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        TransactionForm form = new TransactionForm();
        form.setDate(LocalDate.now(clock));
        model.addAttribute("form", form);
        return formPage(model, "Add transaction", "/transactions");
    }

    @PostMapping
    public String add(@Valid @ModelAttribute("form") TransactionForm form, BindingResult result,
                      RedirectAttributes redirect, Model model) {
        if (!result.hasErrors()) {
            try {
                transactions.add(form.toRequest());
                redirect.addFlashAttribute("message", "Transaction added");
                return "redirect:/transactions";
            } catch (TransactionRuleException e) {
                rejectRules(e, result);
            }
        }
        // Something failed: show the form again with the errors and what the founder typed. Nothing was saved.
        return formPage(model, "Add transaction", "/transactions");
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        Transaction t = transactions.get(id);
        model.addAttribute("form", TransactionForm.from(t));
        model.addAttribute("sharesLocked", t.sharesIssued() != null);
        return formPage(model, "Edit transaction", "/transactions/" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") TransactionForm form, BindingResult result,
                         RedirectAttributes redirect, Model model) {
        if (!result.hasErrors()) {
            try {
                transactions.update(id, form.toRequest());
                redirect.addFlashAttribute("message", "Transaction updated");
                return "redirect:/transactions";
            } catch (TransactionRuleException e) {
                rejectRules(e, result);
            }
        }
        // Same as adding: back to the form with the errors and the founder's input. The saved row is untouched.
        model.addAttribute("sharesLocked", transactions.get(id).sharesIssued() != null);
        return formPage(model, "Edit transaction", "/transactions/" + id);
    }

    private static void rejectRules(TransactionRuleException e, BindingResult result) {
        for (Map.Entry<String, String> error : e.getErrors().entrySet()) {
            result.rejectValue(error.getKey(), "rule", error.getValue());
        }
    }

    private String formPage(Model model, String heading, String action) {
        model.addAttribute("heading", heading);
        model.addAttribute("action", action);
        model.addAttribute("types", TransactionType.values());
        model.addAttribute("categories", TransactionCategory.values());
        model.addAttribute("today", LocalDate.now(clock));
        return "transaction-form";
    }
}
