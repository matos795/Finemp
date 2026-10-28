CREATE TABLE financial_bank_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('CURRENT','SAVINGS','CASH','INVESTMENT','DIGITAL','OTHER')),
    opening_balance NUMERIC(19,2) NOT NULL CHECK (opening_balance <> 'NaN'::numeric),
    opening_date DATE NOT NULL CHECK (opening_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_financial_bank_company_id UNIQUE (company_id,id)
);
CREATE INDEX idx_financial_bank_company_name ON financial_bank_accounts(company_id,name,id);
