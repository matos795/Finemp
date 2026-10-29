-- Receipt is written with the import transaction in a later increment.
CREATE TABLE financial_statement_import_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
    result_state TEXT NOT NULL CHECK(jsonb_typeof(result_state::jsonb)='object'),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id),
    CONSTRAINT fk_statement_import_receipt_bank FOREIGN KEY(company_id,bank_account_id) REFERENCES financial_bank_accounts(company_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_statement_import_receipt_bank ON financial_statement_import_receipts(company_id,bank_account_id);
