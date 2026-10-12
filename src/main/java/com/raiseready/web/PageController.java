package com.raiseready.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.raiseready.captable.CapTableService;
import com.raiseready.investor.InvestorPositionService;
import com.raiseready.metrics.MetricsService;
import com.raiseready.transactions.Transaction;
import com.raiseready.transactions.TransactionCategory;
import com.raiseready.transactions.TransactionService;
import com.raiseready.transactions.TransactionType;

@Controller
public class PageController {

    /** Expense categories in a fixed order, so each keeps the same colour in the chart whichever ones have spending. */
    private static final List<TransactionCategory> EXPENSE_CATEGORIES = new ArrayList<>();

    static {
        for (TransactionCategory category : TransactionCategory.values()) {
            if (category.getType() == TransactionType.EXPENSE) {
                EXPENSE_CATEGORIES.add(category);
            }
        }
    }

    private final InvestmentRepository investments;
    private final MetricsService metrics;
    private final TransactionService transactions;
    private final InrFormatter inr;
    private final CapTableService capTable;
    private final InvestorPositionService investorPositions;
    private final ViewingAs viewingAs;

    public PageController(InvestmentRepository investments, MetricsService metrics, TransactionService transactions, InrFormatter inr,
                          CapTableService capTable, InvestorPositionService investorPositions, ViewingAs viewingAs) {
        this.investments = investments;
        this.metrics = metrics;
        this.transactions = transactions;
        this.inr = inr;
        this.capTable = capTable;
        this.investorPositions = investorPositions;
        this.viewingAs = viewingAs;
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        List<Investment> rows = investments.findAllOldestFirst();
        BigDecimal totalRaised = rows.stream().map(Investment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal equitySold = rows.stream().map(Investment::equityPct).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("companyName", rows.isEmpty() ? null : rows.get(0).companyName());
        model.addAttribute("rounds", rows.size());
        model.addAttribute("totalRaised", totalRaised);
        model.addAttribute("equitySold", equitySold);
        // The company's current valuation, kept up to date each time an equity investment is saved.
        model.addAttribute("latestValuation", capTable.current().currentValuationInr());
        // Investor mode: the numbers of the investor chosen in the "Viewing as" dropdown, if anyone has invested.
        if (ViewMode.from(session) == ViewMode.INVESTOR) {
            model.addAttribute("position", viewingAs.selected(session).map(investorPositions::positionOf).orElse(null));
        }
        // Worked out from the transactions on every load, so a transaction added a moment ago is already in.
        model.addAttribute("metrics", metrics.current());

        List<Transaction> oldestFirst = transactions.listNewestFirst().stream()
                .sorted(Comparator.comparing(Transaction::occurredOn).thenComparing(Transaction::id))
                .toList();
        List<BreakupSegment> moneyIn = MoneyBreakup.group(moneyInEntries(oldestFirst), inr::format);
        List<BreakupSegment> moneyOut = MoneyBreakup.group(moneyOutEntries(oldestFirst), inr::format);
        model.addAttribute("moneyIn", moneyIn);
        model.addAttribute("moneyInDonut", MoneyBreakup.donut(moneyIn));
        model.addAttribute("moneyInTotal", total(moneyIn));
        model.addAttribute("moneyOut", moneyOut);
        model.addAttribute("moneyOutDonut", MoneyBreakup.donut(moneyOut));
        model.addAttribute("moneyOutTotal", total(moneyOut));
        return "index";
    }

    /** Everything that came in: each investor or lender by name, and each kind of revenue. */
    static List<MoneyBreakup.Entry> moneyInEntries(List<Transaction> oldestFirst) {
        List<MoneyBreakup.Entry> entries = new ArrayList<>();
        for (Transaction t : oldestFirst) {
            if (t.type() == TransactionType.FUNDING) {
                entries.add(new MoneyBreakup.Entry(t.investorName() != null ? t.investorName() : t.category().getLabel(), t.occurredOn(), t.amountInr()));
            } else if (t.type() == TransactionType.REVENUE) {
                entries.add(new MoneyBreakup.Entry(t.category().getLabel(), t.occurredOn(), t.amountInr()));
            }
        }
        return entries;
    }

    /** Everything that went out, by expense category. */
    static List<MoneyBreakup.Entry> moneyOutEntries(List<Transaction> oldestFirst) {
        List<MoneyBreakup.Entry> entries = new ArrayList<>();
        for (Transaction t : oldestFirst) {
            if (t.type() == TransactionType.EXPENSE) {
                entries.add(new MoneyBreakup.Entry(t.category().getLabel(), t.occurredOn(), t.amountInr(), EXPENSE_CATEGORIES.indexOf(t.category()) + 1));
            }
        }
        return entries;
    }

    private static BigDecimal total(List<BreakupSegment> segments) {
        return segments.stream().map(BreakupSegment::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @GetMapping("/investments")
    public String investments(Model model) {
        List<Investment> rounds = investments.findAllOldestFirst();
        model.addAttribute("investments", rounds);
        model.addAttribute("equity", EquityChart.of(rounds));
        model.addAttribute("capTable", capTable.current());
        return "investments";
    }
}
