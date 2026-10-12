package com.raiseready.transactions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.raiseready.captable.CapTableRuleException;
import com.raiseready.captable.CapTableService;
import com.raiseready.captable.Dilution;

/**
 * Every business rule for adding a transaction lives here, so the add form, the edit form and the tests
 * all go through the same rules. The controller only checks the mode, calls this and picks the page.
 */
@Service
public class TransactionService {

    static final String SHARES_ISSUED = "Shares were issued for this investment, so this can't be changed. Only the date and description can.";

    private final TransactionRepository repository;
    private final CapTableService capTable;

    public TransactionService(TransactionRepository repository, CapTableService capTable) {
        this.repository = repository;
        this.capTable = capTable;
    }

    /**
     * Applies the rules, then saves. It is one database transaction on purpose: an equity investment also creates
     * the investor's shares and moves the company's valuation, and a failure anywhere undoes all of it.
     *
     * @throws TransactionRuleException if a rule fails; nothing is saved
     */
    @Transactional
    public Transaction add(NewTransaction request) {
        Checked checked = check(request);
        Issue issue = issueShares(request, checked);
        Transaction saved = repository.insert(request.type(), request.category(), request.occurredOn(), checked.amount(),
                checked.description(), checked.investor(), issue.equityPct(), checked.preMoney(), issue.shares(),
                request.category().isRecurring());
        capTable.refreshCurrentValuation();
        return saved;
    }

    /**
     * Replaces everything the founder can edit on an existing transaction, under exactly the same rules as {@link #add}.
     * The recurring flag is worked out again from the (possibly new) category; the created time is left alone.
     * <p>
     * Once an equity investment has created shares, its money terms are fixed: the cap table was built from them, and
     * quietly changing them would make it disagree with the ledger. Only its date and description can change.
     *
     * @throws TransactionNotFoundException if there is no transaction with that id
     * @throws TransactionRuleException     if a rule fails; the saved row is left as it was
     */
    @Transactional
    public Transaction update(long id, NewTransaction request) {
        Checked checked = check(request);
        Transaction existing = repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));

        Issue issue;
        if (existing.sharesIssued() != null) {
            requireSameMoneyTerms(existing, request, checked);
            issue = new Issue(existing.equityPct(), existing.sharesIssued());
        } else {
            issue = issueShares(request, checked);
        }
        Transaction saved = repository.update(id, request.type(), request.category(), request.occurredOn(), checked.amount(),
                        checked.description(), checked.investor(), issue.equityPct(), checked.preMoney(), issue.shares(),
                        request.category().isRecurring())
                .orElseThrow(() -> new TransactionNotFoundException(id));
        capTable.refreshCurrentValuation();
        return saved;
    }

    public Transaction get(long id) {
        return repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    /** What an equity investment turned into: the equity it bought and the shares created. Both null for anything else. */
    private record Issue(BigDecimal equityPct, Long shares) {
    }

    /** For an equity investment, puts the investor on the cap table. A round the cap table can't take is a rule failure. */
    private Issue issueShares(NewTransaction request, Checked checked) {
        if (request.category() != TransactionCategory.EQUITY_INVESTMENT) {
            return new Issue(null, null);
        }
        try {
            Dilution dilution = capTable.issue(checked.investor(), checked.preMoney(), checked.amount());
            return new Issue(dilution.equityPct(), dilution.newShares());
        } catch (CapTableRuleException e) {
            throw new TransactionRuleException(Map.of(e.getField(), e.getMessage()));
        }
    }

    /** An investment that already created shares may only change its date and description. */
    private static void requireSameMoneyTerms(Transaction existing, NewTransaction request, Checked checked) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (existing.type() != request.type()) {
            errors.put("type", SHARES_ISSUED);
        }
        if (existing.category() != request.category()) {
            errors.put("category", SHARES_ISSUED);
        }
        if (existing.amountInr().compareTo(checked.amount()) != 0) {
            errors.put("amount", SHARES_ISSUED);
        }
        if (!existing.investorName().equals(checked.investor())) {
            errors.put("investorName", SHARES_ISSUED);
        }
        if (checked.preMoney() == null || existing.preMoneyValuationInr().compareTo(checked.preMoney()) != 0) {
            errors.put("preMoneyValuation", SHARES_ISSUED);
        }
        if (!errors.isEmpty()) {
            throw new TransactionRuleException(errors);
        }
    }

    /** The business rules, in one place for add and update. Returns the values to save, tidied. */
    private static Checked check(NewTransaction request) {
        Map<String, String> errors = new LinkedHashMap<>();

        // The category must belong to the type that was picked.
        if (request.category().getType() != request.type()) {
            errors.put("category", "Pick a category that belongs to " + request.type().getLabel() + ".");
        }

        // The investor name only means something for funding; elsewhere it is dropped. Spaces are tidied either way.
        String investor = request.type() == TransactionType.FUNDING ? tidy(request.investorName()) : null;
        if (request.category() == TransactionCategory.EQUITY_INVESTMENT && investor == null) {
            errors.put("investorName", "Enter the investor's name for an equity investment.");
        }

        // An equity investment says what the company was worth before the money came in; the equity and the shares
        // follow from that (see DilutionService). Anything else carries no valuation, so a stale value is dropped.
        BigDecimal preMoney = null;
        if (request.category() == TransactionCategory.EQUITY_INVESTMENT) {
            preMoney = request.preMoneyValuationInr() == null ? null : request.preMoneyValuationInr().setScale(2, RoundingMode.HALF_UP);
            if (preMoney == null) {
                errors.put("preMoneyValuation", "Enter the pre-money valuation for an equity investment.");
            } else if (preMoney.signum() <= 0) {
                errors.put("preMoneyValuation", "The pre-money valuation must be at least ₹0.01.");
            }
        }

        BigDecimal amount = request.amountInr().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            errors.put("amount", "The amount must be at least ₹0.01.");
        }

        if (!errors.isEmpty()) {
            throw new TransactionRuleException(errors);
        }
        return new Checked(amount, tidy(request.description()), investor, preMoney);
    }

    private record Checked(BigDecimal amount, String description, String investor, BigDecimal preMoney) {
    }

    public List<Transaction> listNewestFirst() {
        return repository.findAllNewestFirst();
    }

    /** Trims, squeezes runs of spaces into one, and turns "nothing left" into null. */
    static String tidy(String text) {
        if (text == null) {
            return null;
        }
        String tidied = text.strip().replaceAll("\\s+", " ");
        return tidied.isEmpty() ? null : tidied;
    }
}
