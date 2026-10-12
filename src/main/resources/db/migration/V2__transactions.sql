-- Money in and out of the company, one row per transaction. Amounts are in Indian rupees (INR).
-- Cash, burn, runway and MRR are never stored: they are worked out from these rows each time (see MetricsService).
CREATE TABLE transactions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('EXPENSE', 'REVENUE', 'FUNDING')),
    category       VARCHAR(40)   NOT NULL,
    occurred_on    DATE          NOT NULL,
    amount_inr     NUMERIC(15,2) NOT NULL CHECK (amount_inr > 0),
    description    VARCHAR(200),
    investor_name  VARCHAR(200),
    -- Copied from the category when the row is saved (a salary repeats every month, a one-time sale does not).
    recurring      BOOLEAN       NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Backstops for the rules TransactionService enforces.
    CHECK (investor_name IS NULL OR type = 'FUNDING'),
    CHECK (category <> 'EQUITY_INVESTMENT' OR investor_name IS NOT NULL)
);

CREATE INDEX transactions_occurred_on_idx ON transactions (occurred_on);
