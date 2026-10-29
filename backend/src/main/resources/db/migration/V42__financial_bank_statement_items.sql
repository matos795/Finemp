CREATE TABLE financial_bank_statement_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    bank_account_id UUID NOT NULL,
    posted_date DATE NOT NULL CHECK(posted_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    signed_amount NUMERIC(19,2) NOT NULL CHECK(signed_amount<>0 AND signed_amount<>'NaN'::numeric),
    memo VARCHAR(4000) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    document_reference VARCHAR(255) NOT NULL,
    identity_key VARCHAR(64) NOT NULL CHECK(identity_key ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','IGNORED','RECONCILED')),
    linked_entry_id UUID,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT uq_statement_company_id UNIQUE(company_id,id),
    CONSTRAINT uq_statement_identity UNIQUE(company_id,bank_account_id,identity_key),
    CONSTRAINT fk_statement_bank FOREIGN KEY(company_id,bank_account_id) REFERENCES financial_bank_accounts(company_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_statement_entry FOREIGN KEY(company_id,linked_entry_id) REFERENCES financial_entries(company_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_statement_link_state CHECK((status='RECONCILED' AND linked_entry_id IS NOT NULL) OR (status IN ('PENDING','IGNORED') AND linked_entry_id IS NULL))
);
CREATE UNIQUE INDEX uq_statement_linked_entry ON financial_bank_statement_items(company_id,linked_entry_id) WHERE linked_entry_id IS NOT NULL;
CREATE INDEX idx_statement_bank_date ON financial_bank_statement_items(company_id,bank_account_id,posted_date DESC,id);
