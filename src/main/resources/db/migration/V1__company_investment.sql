-- One row per purchase of equity in a company by an investor. Amounts are in Indian rupees (INR).
CREATE TABLE company_investment (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name   VARCHAR(200)  NOT NULL,
    investor_name  VARCHAR(200)  NOT NULL,
    invested_on    DATE          NOT NULL,
    -- Company valuation at the time of this purchase, after the money came in (post-money).
    valuation_inr  NUMERIC(15,2) NOT NULL CHECK (valuation_inr > 0),
    amount_inr     NUMERIC(15,2) NOT NULL CHECK (amount_inr > 0),
    -- Percentage of the company this purchase bought at that valuation: amount / valuation * 100.
    equity_pct     NUMERIC(5,2)  NOT NULL CHECK (equity_pct > 0 AND equity_pct <= 100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (amount_inr <= valuation_inr),
    UNIQUE (company_name, investor_name, invested_on)
);

CREATE INDEX company_investment_company_idx ON company_investment (company_name, invested_on);

-- Demo data (1 lakh = 100,000; 1 crore = 10,000,000): three investors buying in at three different times.
INSERT INTO company_investment (company_name, investor_name, invested_on, valuation_inr, amount_inr, equity_pct) VALUES
    ('RaiseReady Pvt Ltd', 'Ananya Rao',  DATE '2022-04-12',  20000000.00,  2000000.00, 10.00),  -- Seed:     ₹2 Cr,  ₹20 lakh
    ('RaiseReady Pvt Ltd', 'Rohan Mehta', DATE '2023-09-05',  80000000.00,  9600000.00, 12.00),  -- Series A: ₹8 Cr,  ₹96 lakh
    ('RaiseReady Pvt Ltd', 'Sneha Iyer',  DATE '2025-02-18', 200000000.00, 15000000.00,  7.50);  -- Series B: ₹20 Cr, ₹1.5 Cr
