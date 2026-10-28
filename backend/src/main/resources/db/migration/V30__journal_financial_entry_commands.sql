CREATE TABLE financial_entry_command_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    entry_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK(action IN ('CREATED','UPDATED')),
    result_version BIGINT NOT NULL CHECK(result_version>=0),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id)
);
CREATE TABLE financial_entry_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    entry_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK(action IN ('CREATED','UPDATED')),
    before_version BIGINT CHECK(before_version>=0),
    after_version BIGINT NOT NULL CHECK(after_version>=0),
    before_state TEXT CHECK(before_state IS NULL OR jsonb_typeof(before_state::jsonb)='object'),
    after_state TEXT NOT NULL CHECK(jsonb_typeof(after_state::jsonb)='object'),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_financial_entry_event_command UNIQUE(company_id,actor_id,command_id),
    CONSTRAINT ck_financial_entry_event_transition CHECK(
        (action='CREATED' AND before_version IS NULL AND before_state IS NULL AND after_version=0)
        OR (action='UPDATED' AND before_version IS NOT NULL AND before_state IS NOT NULL AND after_version>=before_version))
);
CREATE INDEX idx_financial_entry_events_history ON financial_entry_events(company_id,entry_id,occurred_at,id);
-- No entry FK: deleting an entry later must not erase its command receipt or audit history.
