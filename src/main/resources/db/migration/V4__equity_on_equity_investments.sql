-- An equity investment records how much of the company the money bought. The valuation is not stored: it is
-- worked out from the two (valuation = amount / equity), so it can never disagree with them.
ALTER TABLE transactions ADD COLUMN equity_pct NUMERIC(5,2) CHECK (equity_pct > 0 AND equity_pct <= 100);

-- The rounds copied over from company_investment (V3) already know their equity.
UPDATE transactions t
SET equity_pct = ci.equity_pct
FROM company_investment ci
WHERE t.category = 'EQUITY_INVESTMENT'
  AND t.equity_pct IS NULL
  AND t.investor_name = ci.investor_name
  AND t.occurred_on = ci.invested_on
  AND t.amount_inr = ci.amount_inr;

-- Equity belongs to equity investments only.
ALTER TABLE transactions ADD CONSTRAINT transactions_equity_only_for_equity_investment
    CHECK (equity_pct IS NULL OR category = 'EQUITY_INVESTMENT');

-- Every new or changed equity investment must carry its equity. NOT VALID: rows saved before this column
-- existed are not re-checked, so an old row without equity cannot make this migration fail.
ALTER TABLE transactions ADD CONSTRAINT transactions_equity_investment_needs_equity
    CHECK (category <> 'EQUITY_INVESTMENT' OR equity_pct IS NOT NULL) NOT VALID;
