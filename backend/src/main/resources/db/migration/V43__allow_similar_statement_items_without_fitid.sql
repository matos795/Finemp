-- Fallback identity indicates a potential duplicate, not proof of duplication.
-- Retry safety belongs to the import command receipt, not this similarity key.
ALTER TABLE financial_bank_statement_items DROP CONSTRAINT uq_statement_identity;
CREATE UNIQUE INDEX uq_statement_explicit_identity ON financial_bank_statement_items(company_id,bank_account_id,identity_key) WHERE external_id <> '';
CREATE INDEX idx_statement_fallback_identity ON financial_bank_statement_items(company_id,bank_account_id,identity_key) WHERE external_id = '';
