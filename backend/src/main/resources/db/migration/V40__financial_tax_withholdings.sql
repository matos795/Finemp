CREATE TABLE financial_tax_withholdings (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    entry_id UUID NOT NULL,
    tax_code VARCHAR(40) NOT NULL CHECK(length(trim(tax_code))>0),
    treatment VARCHAR(20) NOT NULL CHECK(treatment IN ('PAYABLE','RECOVERABLE')),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>=0 AND amount<>'NaN'::numeric),
    document_reference VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    updated_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT uq_financial_withholding_company_id UNIQUE(company_id,id),
    CONSTRAINT fk_financial_withholding_entry FOREIGN KEY(company_id,entry_id) REFERENCES financial_entries(company_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_financial_withholding_entry ON financial_tax_withholdings(company_id,entry_id,id);
CREATE INDEX idx_financial_withholding_treatment ON financial_tax_withholdings(company_id,treatment,id);
