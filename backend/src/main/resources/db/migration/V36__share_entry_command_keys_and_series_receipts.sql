CREATE TABLE financial_entry_command_keys (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK(kind IN ('INDIVIDUAL','SERIES_CREATION')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(company_id,actor_id,command_id),
    CONSTRAINT uq_entry_command_key_kind UNIQUE(company_id,actor_id,command_id,kind)
);
INSERT INTO financial_entry_command_keys(company_id,actor_id,command_id,kind,created_at)
    SELECT company_id,actor_id,command_id,'INDIVIDUAL',committed_at FROM financial_entry_command_receipts;

ALTER TABLE financial_entry_command_receipts
    ADD COLUMN key_kind VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL' CHECK(key_kind='INDIVIDUAL'),
    ADD CONSTRAINT fk_entry_receipt_key FOREIGN KEY(company_id,actor_id,command_id,key_kind)
        REFERENCES financial_entry_command_keys(company_id,actor_id,command_id,kind);

CREATE TABLE financial_entry_series_command_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    key_kind VARCHAR(20) NOT NULL DEFAULT 'SERIES_CREATION' CHECK(key_kind='SERIES_CREATION'),
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    series_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 2 AND 120),
    result_entries TEXT NOT NULL CHECK(jsonb_typeof(result_entries::jsonb)='array' AND jsonb_array_length(result_entries::jsonb)=quantity),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id),
    CONSTRAINT fk_entry_series_receipt_key FOREIGN KEY(company_id,actor_id,command_id,key_kind)
        REFERENCES financial_entry_command_keys(company_id,actor_id,command_id,kind)
);
ALTER TABLE financial_entry_events
    DROP CONSTRAINT uq_financial_entry_event_command,
    ADD CONSTRAINT uq_financial_entry_event_command UNIQUE(company_id,actor_id,command_id,entry_id),
    ADD CONSTRAINT fk_financial_entry_event_key FOREIGN KEY(company_id,actor_id,command_id)
        REFERENCES financial_entry_command_keys(company_id,actor_id,command_id);
-- Existing receipt values/hashes survive. Receipt/event history is not tied to live entry/series rows.
