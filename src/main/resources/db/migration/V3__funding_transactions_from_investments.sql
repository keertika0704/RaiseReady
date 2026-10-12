-- Each investor purchase in company_investment is money that came into the company, so it belongs in the
-- transactions ledger too: cash is worked out from transactions only (see MetricsService).
-- Rows that are already in the ledger (same investor, date and amount) are skipped, so nothing is counted twice.
INSERT INTO transactions (type, category, occurred_on, amount_inr, description, investor_name, recurring)
SELECT 'FUNDING', 'EQUITY_INVESTMENT', ci.invested_on, ci.amount_inr, 'Equity investment', ci.investor_name, false
FROM company_investment ci
WHERE NOT EXISTS (
    SELECT 1 FROM transactions t
    WHERE t.type = 'FUNDING' AND t.category = 'EQUITY_INVESTMENT'
      AND t.investor_name = ci.investor_name AND t.occurred_on = ci.invested_on AND t.amount_inr = ci.amount_inr
)
ORDER BY ci.invested_on, ci.id;
