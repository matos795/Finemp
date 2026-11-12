CREATE TABLE contracts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    business_partner_id UUID NOT NULL REFERENCES business_partners(id),
    direction VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    billing_day SMALLINT NOT NULL,
    monthly_amount NUMERIC(19,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_contract_direction CHECK (direction IN ('INCOME','EXPENSE')),
    CONSTRAINT ck_contract_billing_day CHECK (billing_day BETWEEN 1 AND 31),
    CONSTRAINT ck_contract_monthly_amount CHECK (monthly_amount >= 0),
    CONSTRAINT ck_contract_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_contracts_company_active
    ON contracts(company_id, active);

CREATE INDEX idx_contracts_company_partner
    ON contracts(company_id, business_partner_id);
