-- Cap table: who holds how many shares. Percentages are never stored; they are shares / total shares.
CREATE TABLE shareholder (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    shares      BIGINT       NOT NULL CHECK (shares > 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One row per holder, whatever case the name was typed in: a second investment by the same investor adds shares.
CREATE UNIQUE INDEX shareholder_name_key ON shareholder (lower(name));

-- The company starts with the founder holding every share.
INSERT INTO shareholder (name, shares) VALUES ('Founder', 1000000);

-- An equity investment now says what the company was worth BEFORE the money came in (pre-money). The equity it
-- bought and the shares it created are worked out from that (see DilutionService) and kept on the row, so the
-- row records exactly what was issued. Post-money is pre-money + amount, so it is not stored.
ALTER TABLE transactions
    ADD COLUMN pre_money_valuation_inr NUMERIC(15,2) CHECK (pre_money_valuation_inr > 0),
    ADD COLUMN shares_issued           BIGINT        CHECK (shares_issued > 0);

ALTER TABLE transactions ADD CONSTRAINT transactions_cap_table_only_for_equity_investment
    CHECK ((pre_money_valuation_inr IS NULL AND shares_issued IS NULL) OR category = 'EQUITY_INVESTMENT');

-- The rounds already in the ledger knew their equity, not their pre-money: pre-money = amount / equity - amount.
-- (A 100% round has no pre-money, so it is left out.)
UPDATE transactions
SET pre_money_valuation_inr = round(amount_inr * 100 / equity_pct - amount_inr, 2)
WHERE category = 'EQUITY_INVESTMENT' AND equity_pct IS NOT NULL AND equity_pct < 100;

-- Replay those rounds oldest first into the cap table, with the same sum DilutionService does:
-- new shares = shares before the round * amount / pre-money, rounded to a whole share.
DO $$
DECLARE
    r      RECORD;
    total  BIGINT;
    issued BIGINT;
BEGIN
    FOR r IN
        SELECT id, investor_name, amount_inr, pre_money_valuation_inr
        FROM transactions
        WHERE category = 'EQUITY_INVESTMENT'
          AND pre_money_valuation_inr IS NOT NULL
          AND investor_name IS NOT NULL
          AND lower(investor_name) <> 'founder'
        ORDER BY occurred_on, id
    LOOP
        SELECT sum(shares) INTO total FROM shareholder;
        issued := round(total * r.amount_inr / r.pre_money_valuation_inr);
        CONTINUE WHEN issued < 1;

        INSERT INTO shareholder (name, shares) VALUES (r.investor_name, issued)
        ON CONFLICT (lower(name)) DO UPDATE SET shares = shareholder.shares + EXCLUDED.shares;
        UPDATE transactions SET shares_issued = issued WHERE id = r.id;
    END LOOP;
END $$;

-- Every new equity investment must carry its pre-money. NOT VALID, like V4: rows saved before this existed
-- (and the 100% case above) are not re-checked.
ALTER TABLE transactions ADD CONSTRAINT transactions_equity_investment_needs_pre_money
    CHECK (category <> 'EQUITY_INVESTMENT' OR pre_money_valuation_inr IS NOT NULL) NOT VALID;

-- The company itself: one row, holding its current valuation = the post-money of the latest equity round.
CREATE TABLE company (
    id                    SMALLINT PRIMARY KEY CHECK (id = 1),
    name                  VARCHAR(200) NOT NULL,
    current_valuation_inr NUMERIC(15,2) CHECK (current_valuation_inr > 0)
);

INSERT INTO company (id, name, current_valuation_inr)
SELECT 1,
       COALESCE((SELECT company_name FROM company_investment ORDER BY invested_on, id LIMIT 1), 'RaiseReady Pvt Ltd'),
       (SELECT pre_money_valuation_inr + amount_inr
        FROM transactions
        WHERE shares_issued IS NOT NULL
        ORDER BY occurred_on DESC, id DESC
        LIMIT 1);
