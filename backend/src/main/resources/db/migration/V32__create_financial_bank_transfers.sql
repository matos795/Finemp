CREATE TABLE financial_bank_transfers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    origin_bank_account_id UUID NOT NULL,
    destination_bank_account_id UUID NOT NULL,
    transfer_date DATE NOT NULL CHECK(transfer_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>=0 AND amount<>'NaN'::numeric),
    description VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT uq_bank_transfer_company_id UNIQUE(company_id,id),
    CONSTRAINT fk_bank_transfer_origin FOREIGN KEY(company_id,origin_bank_account_id) REFERENCES financial_bank_accounts(company_id,id),
    CONSTRAINT fk_bank_transfer_destination FOREIGN KEY(company_id,destination_bank_account_id) REFERENCES financial_bank_accounts(company_id,id),
    CONSTRAINT ck_bank_transfer_distinct_accounts CHECK(origin_bank_account_id<>destination_bank_account_id)
);
CREATE INDEX idx_bank_transfer_origin ON financial_bank_transfers(company_id,origin_bank_account_id);
CREATE INDEX idx_bank_transfer_destination ON financial_bank_transfers(company_id,destination_bank_account_id);
CREATE INDEX idx_bank_transfer_date ON financial_bank_transfers(company_id,transfer_date,id);
-- Storage does not choose raw capture normalization, operational authorization or derived bank balances.
