CREATE TABLE financial_entry_series (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    mode VARCHAR(20) NOT NULL CHECK(mode IN ('MONTHLY','INSTALLMENTS')),
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 2 AND 120),
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT uq_entry_series_company_id UNIQUE(company_id,id),
    CONSTRAINT uq_entry_series_company_id_quantity UNIQUE(company_id,id,quantity)
);
CREATE TABLE financial_entry_series_members (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    series_id UUID NOT NULL,
    entry_id UUID NOT NULL,
    occurrence_number INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 2 AND 120),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    CONSTRAINT ck_entry_series_member_number CHECK(occurrence_number BETWEEN 1 AND quantity),
    CONSTRAINT uq_entry_series_member_entry UNIQUE(company_id,entry_id),
    CONSTRAINT uq_entry_series_member_number UNIQUE(company_id,series_id,occurrence_number),
    CONSTRAINT fk_entry_series_member_series FOREIGN KEY(company_id,series_id,quantity)
        REFERENCES financial_entry_series(company_id,id,quantity) ON DELETE RESTRICT,
    CONSTRAINT fk_entry_series_member_entry FOREIGN KEY(company_id,entry_id)
        REFERENCES financial_entries(company_id,id) ON DELETE RESTRICT
);
-- Do not expose series creation until coordinated deletion/audit/idempotency are integrated.
-- No monetary allocation, date generation, background scheduler or automatic entry cascade.
