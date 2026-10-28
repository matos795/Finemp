CREATE TABLE financial_entries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    direction VARCHAR(20) NOT NULL CHECK(direction IN ('INCOME','EXPENSE')),
    description VARCHAR(255) NOT NULL CHECK(length(trim(description)) >= 2),
    nominal_amount NUMERIC(19,2) NOT NULL CHECK(nominal_amount>=0 AND nominal_amount<>'NaN'::numeric),
    document_date DATE NOT NULL CHECK(document_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    entry_date DATE NOT NULL CHECK(entry_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    competence VARCHAR(7) NOT NULL CHECK(competence ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND left(competence,4)<>'0000'),
    due_date DATE NOT NULL CHECK(due_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    payment_date DATE CHECK(payment_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    actual_paid_amount NUMERIC(19,2) CHECK(actual_paid_amount>=0 AND actual_paid_amount<>'NaN'::numeric),
    bank_account_id UUID NOT NULL,
    category_id UUID NOT NULL,
    partner_id UUID,
    result_center_id UUID,
    cost_behavior_override VARCHAR(20) CHECK(cost_behavior_override IN ('FIXED','VARIABLE')),
    payment_method VARCHAR(80) NOT NULL,
    document_number VARCHAR(255) NOT NULL,
    notes VARCHAR(4000) NOT NULL,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    updated_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT uq_financial_entry_company_id UNIQUE(company_id,id),
    CONSTRAINT fk_financial_entry_bank FOREIGN KEY(company_id,bank_account_id) REFERENCES financial_bank_accounts(company_id,id),
    CONSTRAINT fk_financial_entry_category FOREIGN KEY(company_id,category_id) REFERENCES financial_categories(company_id,id),
    CONSTRAINT fk_financial_entry_partner FOREIGN KEY(company_id,partner_id) REFERENCES business_partners(company_id,id),
    CONSTRAINT fk_financial_entry_center FOREIGN KEY(company_id,result_center_id) REFERENCES operational_result_centers(company_id,id),
    CONSTRAINT ck_financial_entry_paid_state CHECK(payment_date IS NOT NULL OR actual_paid_amount IS NULL)
);
CREATE INDEX idx_financial_entry_due ON financial_entries(company_id,due_date,id);
CREATE INDEX idx_financial_entry_competence ON financial_entries(company_id,competence,id);
CREATE INDEX idx_financial_entry_bank ON financial_entries(company_id,bank_account_id);
CREATE INDEX idx_financial_entry_category ON financial_entries(company_id,category_id);
CREATE INDEX idx_financial_entry_partner ON financial_entries(company_id,partner_id);
CREATE INDEX idx_financial_entry_center ON financial_entries(company_id,result_center_id);
